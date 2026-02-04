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
import com.ordererp.backend.purchase.dto.PurInboundCreateLineRequest;
import com.ordererp.backend.purchase.dto.PurInboundCreateRequest;
import com.ordererp.backend.purchase.dto.PurOrderCreateRequest;
import com.ordererp.backend.purchase.dto.PurOrderLineRequest;
import com.ordererp.backend.purchase.repository.PurOrderDetailRepository;
import com.ordererp.backend.purchase.service.PurInboundService;
import com.ordererp.backend.purchase.service.PurOrderService;
import com.ordererp.backend.wms.entity.WmsStock;
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
 * 第四阶段（Purchase）P0 扩展：分批入库集成测试。
 *
 * 覆盖点：
 * - 同一采购单可多次入库（多张 pur_inbound）
 * - 采购单状态：已审核 -> 部分入库(3) -> 已完成(4)
 * - 幂等：同 requestNo 重复提交不重复加库存
 */
class PurchaseStage4PartialInboundIT {
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
    PurInboundService inboundService;

    @Autowired
    BasePartnerRepository partnerRepository;

    @Autowired
    BaseWarehouseRepository warehouseRepository;

    @Autowired
    BaseProductRepository productRepository;

    @Autowired
    PurOrderDetailRepository orderDetailRepository;

    @Autowired
    WmsStockRepository stockRepository;

    @Test
    @Transactional
    void purchase_partial_inbound_flow_and_idempotent_request_no() {
        BasePartner supplier = createSupplier("SUP-TC-PI-1");
        BaseWarehouse wh = createWarehouse("WH-TC-PI-1");
        BaseProduct p = createProduct("SKU-TC-PI-1");

        var created = orderService.create(new PurOrderCreateRequest(
                supplier.getId(),
                null,
                "tc partial inbound",
                List.of(new PurOrderLineRequest(p.getId(), new BigDecimal("10.00"), new BigDecimal("2.000")))), "tester");
        var audited = orderService.audit(created.id(), "auditor");
        assertEquals(2, audited.status());

        var inbound1 = inboundService.createAndExecuteFromOrder(created.id(), new PurInboundCreateRequest(
                "REQ-TC-PI-1",
                wh.getId(),
                "batch-1",
                List.of(new PurInboundCreateLineRequest(p.getId(), new BigDecimal("1.000")))), "operator");
        assertNotNull(inbound1.inboundId());
        assertNotNull(inbound1.wmsBillNo());
        assertEquals(3, inbound1.orderStatus());

        var od1 = orderDetailRepository.findByOrderIdOrderByIdAsc(created.id()).get(0);
        assertTrue(od1.getInQty().compareTo(new BigDecimal("1.000")) == 0);
        WmsStock stock1 = stockRepository.findFirstByWarehouseIdAndProductId(wh.getId(), p.getId()).orElseThrow();
        assertTrue(stock1.getStockQty().compareTo(new BigDecimal("1.000")) == 0);

        var inbound2 = inboundService.createAndExecuteFromOrder(created.id(), new PurInboundCreateRequest(
                "REQ-TC-PI-2",
                wh.getId(),
                "batch-2",
                List.of(new PurInboundCreateLineRequest(p.getId(), new BigDecimal("1.000")))), "operator");
        assertNotNull(inbound2.inboundId());
        assertNotNull(inbound2.wmsBillNo());
        assertEquals(4, inbound2.orderStatus());

        var od2 = orderDetailRepository.findByOrderIdOrderByIdAsc(created.id()).get(0);
        assertTrue(od2.getInQty().compareTo(new BigDecimal("2.000")) == 0);
        WmsStock stock2 = stockRepository.findFirstByWarehouseIdAndProductId(wh.getId(), p.getId()).orElseThrow();
        assertTrue(stock2.getStockQty().compareTo(new BigDecimal("2.000")) == 0);

        // same requestNo -> idempotent
        var inbound2Retry = inboundService.createAndExecuteFromOrder(created.id(), new PurInboundCreateRequest(
                "REQ-TC-PI-2",
                wh.getId(),
                "batch-2",
                List.of(new PurInboundCreateLineRequest(p.getId(), new BigDecimal("1.000")))), "operator");
        assertEquals(inbound2.inboundId(), inbound2Retry.inboundId());
        assertEquals(inbound2.wmsBillNo(), inbound2Retry.wmsBillNo());

        WmsStock stock3 = stockRepository.findFirstByWarehouseIdAndProductId(wh.getId(), p.getId()).orElseThrow();
        assertTrue(stock3.getStockQty().compareTo(new BigDecimal("2.000")) == 0);
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

