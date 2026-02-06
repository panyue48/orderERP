package com.ordererp.backend.sales;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ordererp.backend.base.entity.BasePartner;
import com.ordererp.backend.base.entity.BaseProduct;
import com.ordererp.backend.base.entity.BaseWarehouse;
import com.ordererp.backend.base.repository.BasePartnerRepository;
import com.ordererp.backend.base.repository.BaseProductRepository;
import com.ordererp.backend.base.repository.BaseWarehouseRepository;
import com.ordererp.backend.sales.dto.SalReturnCreateRequest;
import com.ordererp.backend.sales.dto.SalReturnLineRequest;
import com.ordererp.backend.sales.service.SalReturnService;
import com.ordererp.backend.wms.entity.WmsStock;
import com.ordererp.backend.wms.repository.WmsIoBillDetailRepository;
import com.ordererp.backend.wms.repository.WmsIoBillRepository;
import com.ordererp.backend.wms.repository.WmsStockLogRepository;
import com.ordererp.backend.wms.repository.WmsStockRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest
@Testcontainers
/**
 * 第五阶段（Sales）P1：销售退货集成测试（Testcontainers + MySQL）。
 *
 * 覆盖点：
 * - 退货单创建：保存主表+明细，金额计算正确
 * - 审核：状态流转
 * - 执行：生成 WMS 入库单(type=6)，增加库存并写入 SALES_RETURN 流水
 * - 幂等：重复执行不重复加库存、不重复写流水
 */
class SalesStage5ReturnIT {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.36")
            .withDatabaseName("erp_data")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void mysqlProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("app.datasource.expected-database", () -> "erp_data");
        registry.add("app.datasource.fail-on-mismatch", () -> "true");
    }

    @Autowired
    SalReturnService returnService;

    @Autowired
    BasePartnerRepository partnerRepository;

    @Autowired
    BaseWarehouseRepository warehouseRepository;

    @Autowired
    BaseProductRepository productRepository;

    @Autowired
    WmsStockRepository stockRepository;

    @Autowired
    WmsStockLogRepository stockLogRepository;

    @Autowired
    WmsIoBillRepository ioBillRepository;

    @Autowired
    WmsIoBillDetailRepository ioBillDetailRepository;

    @Test
    @Transactional
    void sales_return_flow_audit_execute_and_idempotent() {
        BasePartner customer = createCustomer("CUS-TC-RET-1");
        BaseWarehouse wh = createWarehouse("WH-TC-SRET-1");
        BaseProduct p = createProduct("SKU-TC-SRET-1");

        // start with 0 stock; sales return execute should upsert/increase stock
        WmsStock stock = new WmsStock();
        stock.setWarehouseId(wh.getId());
        stock.setProductId(p.getId());
        stock.setStockQty(BigDecimal.ZERO);
        stock.setLockedQty(BigDecimal.ZERO);
        stock.setVersion(0);
        stock.setUpdateTime(LocalDateTime.now());
        stockRepository.saveAndFlush(stock);

        var created = returnService.create(new SalReturnCreateRequest(
                customer.getId(),
                wh.getId(),
                LocalDate.now(),
                "tc sales return",
                List.of(new SalReturnLineRequest(p.getId(), new BigDecimal("2.000"), new BigDecimal("10.00")))), "tester");
        assertNotNull(created.id());
        assertEquals(1, created.status());
        assertTrue(created.totalAmount().compareTo(new BigDecimal("20.00")) == 0);

        var audited = returnService.audit(created.id(), "auditor");
        assertEquals(2, audited.status());

        var exec1 = returnService.execute(created.id(), "operator");
        assertEquals(4, exec1.status());
        assertNotNull(exec1.wmsBillNo());

        WmsStock stock1 = stockRepository.findFirstByWarehouseIdAndProductId(wh.getId(), p.getId()).orElseThrow();
        assertTrue(stock1.getStockQty().compareTo(new BigDecimal("2.000")) == 0);

        assertTrue(ioBillRepository.findFirstByBizIdAndType(created.id(), 6).isPresent());
        assertEquals(1, ioBillDetailRepository.findByBillId(exec1.wmsBillId()).size());
        assertEquals(1L, stockLogRepository.countByBizNoAndBizType(exec1.wmsBillNo(), "SALES_RETURN"));

        var exec2 = returnService.execute(created.id(), "operator");
        assertEquals(exec1.wmsBillId(), exec2.wmsBillId());
        assertEquals(exec1.wmsBillNo(), exec2.wmsBillNo());

        WmsStock stock2 = stockRepository.findFirstByWarehouseIdAndProductId(wh.getId(), p.getId()).orElseThrow();
        assertTrue(stock2.getStockQty().compareTo(new BigDecimal("2.000")) == 0);
        assertEquals(1L, stockLogRepository.countByBizNoAndBizType(exec1.wmsBillNo(), "SALES_RETURN"));
    }

    private BasePartner createCustomer(String code) {
        BasePartner p = new BasePartner();
        p.setPartnerCode(code);
        p.setPartnerName(code);
        p.setType(2);
        p.setStatus(1);
        p.setDeleted(0);
        p.setCreateTime(LocalDateTime.now());
        p.setUpdateTime(LocalDateTime.now());
        return partnerRepository.saveAndFlush(p);
    }

    private BaseWarehouse createWarehouse(String code) {
        BaseWarehouse wh = new BaseWarehouse();
        wh.setWarehouseCode(code);
        wh.setWarehouseName(code);
        wh.setStatus(1);
        wh.setDeleted(0);
        wh.setCreateTime(LocalDateTime.now());
        wh.setUpdateTime(LocalDateTime.now());
        return warehouseRepository.saveAndFlush(wh);
    }

    private BaseProduct createProduct(String code) {
        BaseProduct p = new BaseProduct();
        p.setProductCode(code);
        p.setProductName(code);
        p.setUnit("个");
        p.setPurchasePrice(new BigDecimal("10.00"));
        p.setSalePrice(new BigDecimal("12.00"));
        p.setStatus(1);
        p.setDeleted(0);
        p.setCreateTime(LocalDateTime.now());
        p.setUpdateTime(LocalDateTime.now());
        return productRepository.saveAndFlush(p);
    }
}

