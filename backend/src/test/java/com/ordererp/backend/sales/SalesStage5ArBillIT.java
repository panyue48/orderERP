package com.ordererp.backend.sales;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ordererp.backend.base.entity.BasePartner;
import com.ordererp.backend.base.entity.BaseProduct;
import com.ordererp.backend.base.entity.BaseWarehouse;
import com.ordererp.backend.base.repository.BasePartnerRepository;
import com.ordererp.backend.base.repository.BaseProductRepository;
import com.ordererp.backend.base.repository.BaseWarehouseRepository;
import com.ordererp.backend.sales.dto.SalArBillCreateRequest;
import com.ordererp.backend.sales.dto.SalArInvoiceCreateRequest;
import com.ordererp.backend.sales.dto.SalArReceiptCreateRequest;
import com.ordererp.backend.sales.dto.SalOrderCreateRequest;
import com.ordererp.backend.sales.dto.SalReturnCreateRequest;
import com.ordererp.backend.sales.dto.SalReturnLineRequest;
import com.ordererp.backend.sales.service.SalArBillService;
import com.ordererp.backend.sales.service.SalOrderService;
import com.ordererp.backend.sales.service.SalReturnService;
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
 * 第五阶段（Sales）P2：销售对账单（AR）闭环集成测试（Testcontainers + MySQL）。
 *
 * 覆盖点：
 * - 销售订单：创建 -> 审核锁库 -> 发货（生成 sal_ship）
 * - 销售退货：创建 -> 审核 -> 执行（生成 sal_return，入库）
 * - AR 对账单：按客户 + 周期汇总 ship(+) + return(-)，并通过 doc_ref 防重复对账
 * - 审核后允许登记收款/发票；收款累计影响状态（已审核/部分已收/已结清）
 * - 已发生收款/开票后禁止作废
 */
class SalesStage5ArBillIT {
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
    SalReturnService returnService;

    @Autowired
    SalArBillService arBillService;

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
    void sales_ar_bill_flow_create_audit_receipt_invoice_and_doc_lock() {
        BasePartner customer = createCustomer("CUS-TC-AR-1");
        BaseWarehouse wh = createWarehouse("WH-TC-AR-1");
        BaseProduct p = createProduct("SKU-TC-AR-1");

        // Prepare stock for sales shipment (audit locks stock; ship deducts stock).
        WmsStock stock = new WmsStock();
        stock.setWarehouseId(wh.getId());
        stock.setProductId(p.getId());
        stock.setStockQty(new BigDecimal("10.000"));
        stock.setLockedQty(BigDecimal.ZERO);
        stock.setVersion(0);
        stock.setUpdateTime(LocalDateTime.now());
        stockRepository.saveAndFlush(stock);

        var order = orderService.create(new SalOrderCreateRequest(
                customer.getId(),
                wh.getId(),
                LocalDate.now(),
                "tc sales order",
                List.of(new SalOrderCreateRequest.SalOrderLineRequest(p.getId(), new BigDecimal("2.000"), new BigDecimal("12.00")))), "tester");
        assertNotNull(order.id());
        assertEquals(1, order.status());

        var audited = orderService.audit(order.id(), "auditor");
        assertEquals(2, audited.status());

        var shipped = orderService.ship(order.id(), "shipper");
        assertTrue(shipped.status() == 3 || shipped.status() == 4);

        var sret = returnService.create(new SalReturnCreateRequest(
                customer.getId(),
                wh.getId(),
                LocalDate.now(),
                "tc sales return",
                List.of(new SalReturnLineRequest(p.getId(), new BigDecimal("1.000"), new BigDecimal("12.00")))), "tester");
        assertNotNull(sret.id());
        assertEquals(1, sret.status());
        assertTrue(sret.totalAmount().compareTo(new BigDecimal("12.00")) == 0);

        var sretAudited = returnService.audit(sret.id(), "auditor");
        assertEquals(2, sretAudited.status());
        var sretExec = returnService.execute(sret.id(), "operator");
        assertEquals(4, sretExec.status());

        LocalDate today = LocalDate.now();
        var bill = arBillService.create(new SalArBillCreateRequest(customer.getId(), today, today, "tc ar"), "tester");
        assertNotNull(bill.id());
        assertEquals(1, bill.status());
        assertTrue(new BigDecimal("12.00").compareTo(bill.totalAmount()) == 0);

        // Doc lock: same ship/return should not be eligible again
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> arBillService.create(new SalArBillCreateRequest(customer.getId(), today, today, "tc ar2"), "tester"));
        assertEquals(400, ex.getStatusCode().value());

        var auditedBill = arBillService.audit(bill.id(), "auditor");
        assertEquals(2, auditedBill.status());

        var r1 = arBillService.addReceipt(bill.id(), new SalArReceiptCreateRequest(today, new BigDecimal("10.00"), "bank", "part"), "cashier");
        assertNotNull(r1.id());
        var d1 = arBillService.detail(bill.id());
        assertEquals(3, d1.bill().status());
        assertTrue(new BigDecimal("10.00").compareTo(d1.bill().receivedAmount()) == 0);

        var r2 = arBillService.addReceipt(bill.id(), new SalArReceiptCreateRequest(today, new BigDecimal("2.00"), "bank", "rest"), "cashier");
        assertNotNull(r2.id());
        var d2 = arBillService.detail(bill.id());
        assertEquals(4, d2.bill().status());
        assertTrue(new BigDecimal("12.00").compareTo(d2.bill().receivedAmount()) == 0);

        var inv = arBillService.addInvoice(bill.id(), new SalArInvoiceCreateRequest("INV-TC-AR-1", today, new BigDecimal("12.00"),
                new BigDecimal("0.00"), "invoice"), "accountant");
        assertNotNull(inv.id());
        var d3 = arBillService.detail(bill.id());
        assertTrue(new BigDecimal("12.00").compareTo(d3.bill().invoiceAmount()) == 0);

        // With receipts/invoices, cancel should be forbidden
        ResponseStatusException cancelEx = assertThrows(ResponseStatusException.class, () -> arBillService.cancel(bill.id(), "admin"));
        assertEquals(400, cancelEx.getStatusCode().value());
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

