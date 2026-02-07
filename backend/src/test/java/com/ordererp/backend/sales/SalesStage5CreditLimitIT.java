package com.ordererp.backend.sales;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ordererp.backend.base.entity.BasePartner;
import com.ordererp.backend.base.entity.BaseProduct;
import com.ordererp.backend.base.entity.BaseWarehouse;
import com.ordererp.backend.base.repository.BasePartnerRepository;
import com.ordererp.backend.base.repository.BaseProductRepository;
import com.ordererp.backend.base.repository.BaseWarehouseRepository;
import com.ordererp.backend.sales.dto.SalOrderCreateRequest;
import com.ordererp.backend.sales.service.SalOrderService;
import com.ordererp.backend.wms.entity.WmsStock;
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
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

@SpringBootTest
@Testcontainers
/**
 * 第五阶段（Sales）：客户信用额度 / 欠款控制（Testcontainers + MySQL）
 *
 * 覆盖点：
 * - base_partner.credit_limit > 0 时启用额度控制
 * - 审核销售订单前做额度校验：已欠款/未对账发货/未发完订单占用 + 本次新增 <= credit_limit
 */
class SalesStage5CreditLimitIT {
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
    SalOrderService orderService;

    @Autowired
    BasePartnerRepository partnerRepository;

    @Autowired
    BaseWarehouseRepository warehouseRepository;

    @Autowired
    BaseProductRepository productRepository;

    @Autowired
    WmsStockRepository stockRepository;

    @Test
    @Transactional
    void credit_limit_blocks_audit_when_over_limit() {
        BasePartner customer = createCustomer("CUS-TC-CR-OVER", new BigDecimal("10.00"));
        BaseWarehouse wh = createWarehouse("WH-TC-CR-OVER");
        BaseProduct p = createProduct("SKU-TC-CR-OVER");
        prepareStock(wh.getId(), p.getId(), new BigDecimal("10.000"));

        var order = orderService.create(new SalOrderCreateRequest(
                customer.getId(),
                wh.getId(),
                LocalDate.now(),
                "tc credit over",
                List.of(new SalOrderCreateRequest.SalOrderLineRequest(p.getId(), new BigDecimal("1.000"), new BigDecimal("12.00")))), "tester");
        assertNotNull(order.id());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> orderService.audit(order.id(), "auditor"));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    @Transactional
    void credit_limit_counts_open_audited_orders() {
        BasePartner customer = createCustomer("CUS-TC-CR-OPEN", new BigDecimal("20.00"));
        BaseWarehouse wh = createWarehouse("WH-TC-CR-OPEN");
        BaseProduct p = createProduct("SKU-TC-CR-OPEN");
        prepareStock(wh.getId(), p.getId(), new BigDecimal("10.000"));

        var o1 = orderService.create(new SalOrderCreateRequest(
                customer.getId(),
                wh.getId(),
                LocalDate.now(),
                "tc credit o1",
                List.of(new SalOrderCreateRequest.SalOrderLineRequest(p.getId(), new BigDecimal("1.000"), new BigDecimal("12.00")))), "tester");
        assertNotNull(o1.id());
        var a1 = orderService.audit(o1.id(), "auditor");
        assertEquals(2, a1.status());

        var o2 = orderService.create(new SalOrderCreateRequest(
                customer.getId(),
                wh.getId(),
                LocalDate.now(),
                "tc credit o2",
                List.of(new SalOrderCreateRequest.SalOrderLineRequest(p.getId(), new BigDecimal("1.000"), new BigDecimal("12.00")))), "tester");
        assertNotNull(o2.id());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> orderService.audit(o2.id(), "auditor"));
        assertEquals(400, ex.getStatusCode().value());
    }

    private void prepareStock(Long warehouseId, Long productId, BigDecimal qty) {
        WmsStock stock = new WmsStock();
        stock.setWarehouseId(warehouseId);
        stock.setProductId(productId);
        stock.setStockQty(qty);
        stock.setLockedQty(BigDecimal.ZERO);
        stock.setVersion(0);
        stock.setUpdateTime(LocalDateTime.now());
        stockRepository.saveAndFlush(stock);
    }

    private BasePartner createCustomer(String code, BigDecimal creditLimit) {
        BasePartner p = new BasePartner();
        p.setPartnerCode(code);
        p.setPartnerName(code);
        p.setType(2);
        p.setCreditLimit(creditLimit);
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

