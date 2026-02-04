package com.ordererp.backend.purchase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ordererp.backend.base.entity.BasePartner;
import com.ordererp.backend.base.entity.BaseProduct;
import com.ordererp.backend.base.entity.BaseWarehouse;
import com.ordererp.backend.base.repository.BasePartnerRepository;
import com.ordererp.backend.base.repository.BaseProductRepository;
import com.ordererp.backend.base.repository.BaseWarehouseRepository;
import com.ordererp.backend.purchase.dto.PurOrderCreateRequest;
import com.ordererp.backend.purchase.dto.PurOrderInboundRequest;
import com.ordererp.backend.purchase.dto.PurOrderLineRequest;
import com.ordererp.backend.purchase.service.PurOrderService;
import com.ordererp.backend.wms.entity.WmsStock;
import com.ordererp.backend.wms.repository.WmsIoBillDetailRepository;
import com.ordererp.backend.wms.repository.WmsIoBillRepository;
import com.ordererp.backend.wms.repository.WmsStockLogRepository;
import com.ordererp.backend.wms.repository.WmsStockRepository;
import java.math.BigDecimal;
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
 * 第四阶段（Purchase）集成测试（Testcontainers + MySQL）。
 *
 * 覆盖点：
 * - 采购单创建：保存主表+明细，金额计算正确
 * - 采购单审核：状态流转 + 审核字段落库
 * - 采购入库：生成采购入库 WMS 单（type=1），增加库存并写入 PURCHASE_IN 流水
 * - 入库幂等：重复入库返回同一张入库单，不重复加库存、不重复写流水
 */
class PurchaseStage4IT {
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
    PurOrderService orderService;

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
    void purchase_flow_create_audit_inbound_and_idempotent() {
        BasePartner supplier = createSupplier("SUP-TC-1");
        BaseWarehouse wh = createWarehouse("WH-TC-PUR-1");
        BaseProduct p = createProduct("SKU-TC-PUR-1");

        var created = orderService.create(new PurOrderCreateRequest(
                supplier.getId(),
                null,
                "tc purchase",
                List.of(new PurOrderLineRequest(p.getId(), new BigDecimal("10.00"), new BigDecimal("2.000")))), "tester");
        assertNotNull(created.id());
        assertEquals(1, created.status());
        assertTrue(created.totalAmount().compareTo(new BigDecimal("20.00")) == 0);

        var audited = orderService.audit(created.id(), "auditor");
        assertEquals(2, audited.status());
        assertNotNull(audited.auditBy());
        assertNotNull(audited.auditTime());

        var inbound1 = orderService.inbound(created.id(), new PurOrderInboundRequest(wh.getId()), "operator");
        assertEquals(4, inbound1.orderStatus());
        assertNotNull(inbound1.wmsBillNo());

        WmsStock stock = stockRepository.findFirstByWarehouseIdAndProductId(wh.getId(), p.getId()).orElseThrow();
        assertTrue(stock.getStockQty().compareTo(new BigDecimal("2.000")) == 0);

        assertTrue(ioBillRepository.findFirstByBizNoAndType(created.orderNo(), 1).isPresent());
        assertEquals(1, ioBillDetailRepository.findByBillId(inbound1.wmsBillId()).size());
        assertEquals(1L, stockLogRepository.countByBizNoAndBizType(inbound1.wmsBillNo(), "PURCHASE_IN"));

        var inbound2 = orderService.inbound(created.id(), new PurOrderInboundRequest(wh.getId()), "operator");
        assertEquals(inbound1.wmsBillId(), inbound2.wmsBillId());
        assertEquals(inbound1.wmsBillNo(), inbound2.wmsBillNo());

        WmsStock stock2 = stockRepository.findFirstByWarehouseIdAndProductId(wh.getId(), p.getId()).orElseThrow();
        assertTrue(stock2.getStockQty().compareTo(new BigDecimal("2.000")) == 0);
        assertEquals(1L, stockLogRepository.countByBizNoAndBizType(inbound1.wmsBillNo(), "PURCHASE_IN"));
    }

    private BasePartner createSupplier(String code) {
        BasePartner p = new BasePartner();
        p.setPartnerCode(code);
        p.setPartnerName(code);
        p.setType(1);
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
