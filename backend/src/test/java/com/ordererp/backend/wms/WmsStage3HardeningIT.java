package com.ordererp.backend.wms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ordererp.backend.base.entity.BaseProduct;
import com.ordererp.backend.base.entity.BaseWarehouse;
import com.ordererp.backend.base.repository.BaseProductRepository;
import com.ordererp.backend.base.repository.BaseWarehouseRepository;
import com.ordererp.backend.wms.dto.StockInBillCreateRequest;
import com.ordererp.backend.wms.dto.StockInBillLineRequest;
import com.ordererp.backend.wms.dto.StockOutBillCreateRequest;
import com.ordererp.backend.wms.dto.StockOutBillLineRequest;
import com.ordererp.backend.wms.dto.WmsCheckBillCreateRequest;
import com.ordererp.backend.wms.dto.WmsCheckBillLineRequest;
import com.ordererp.backend.wms.entity.WmsStock;
import com.ordererp.backend.wms.repository.WmsCheckBillDetailRepository;
import com.ordererp.backend.wms.repository.WmsStockLogRepository;
import com.ordererp.backend.wms.repository.WmsStockRepository;
import com.ordererp.backend.wms.service.WmsCheckBillService;
import com.ordererp.backend.wms.service.WmsStockInBillService;
import com.ordererp.backend.wms.service.WmsStockOutBillService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
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
 * 第三阶段（WMS Core）“工程化与健壮性拓展”集成测试（Testcontainers + MySQL）。
 *
 * 覆盖点（对应第三阶段文档第 6 章的落地项）：
 * - 幂等：重复执行/重复冲销的结果应稳定（不会重复扣/加库存、不会重复写流水）
 * - 并发基础防护：冲销通过 DB 唯一约束 + 代码兜底返回 existing（这里用“重复调用”验证幂等语义）
 * - 业务校验：库存不足时应返回 400（BAD_REQUEST）
 * - 盘点模型升级：实盘数量（counted_qty）执行后生成调整单并写入流水；重复执行仍幂等
 *
 * 说明：
 * - 测试会启动一个临时 MySQL 容器，并通过 Flyway 自动迁移建表。
 * - 测试数据在每个 @Test 内部创建（仓库/商品/库存/单据）。
 */
class WmsStage3HardeningIT {
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
    BaseWarehouseRepository warehouseRepository;

    @Autowired
    BaseProductRepository productRepository;

    @Autowired
    WmsStockRepository stockRepository;

    @Autowired
    WmsStockLogRepository stockLogRepository;

    @Autowired
    WmsStockInBillService stockInBillService;

    @Autowired
    WmsStockOutBillService stockOutBillService;

    @Autowired
    WmsCheckBillService checkBillService;

    @Autowired
    WmsCheckBillDetailRepository checkBillDetailRepository;

    @Test
    @Transactional
    void stockInExecute_isIdempotent() {
        // 用例 1：盘点入库单执行幂等
        // - 第一次 execute：写入库存 + 写入 STOCK_IN 流水
        // - 第二次 execute：直接返回“已完成”状态，不重复写流水、不重复加库存
        BaseWarehouse wh = createWarehouse("WH-TC-1");
        BaseProduct p = createProduct("SKU-TC-1");

        var created = stockInBillService.create(new StockInBillCreateRequest(
                wh.getId(),
                "tc stock-in",
                List.of(new StockInBillLineRequest(p.getId(), new BigDecimal("2.000")))), "tester");

        var executed1 = stockInBillService.execute(created.id());
        var executed2 = stockInBillService.execute(created.id());

        assertEquals(created.id(), executed1.id());
        assertEquals(created.id(), executed2.id());
        assertEquals(2, executed2.status());

        WmsStock stock = stockRepository.findFirstByWarehouseIdAndProductId(wh.getId(), p.getId()).orElseThrow();
        assertTrue(stock.getStockQty().compareTo(new BigDecimal("2.000")) == 0);
        assertEquals(1L, stockLogRepository.countByBizNoAndBizType(created.billNo(), "STOCK_IN"));
    }

    @Test
    @Transactional
    void reverse_isIdempotent_andUsesUniqueGuard() {
        // 用例 2：盘点入库单冲销幂等
        // - 冲销会创建“冲销单”（实际实现为 stock-out 类型），扣减库存并写 REVERSAL_IN 流水
        // - 重复调用 reverse：应返回同一张冲销单（不重复扣库存、不重复写流水）
        BaseWarehouse wh = createWarehouse("WH-TC-2");
        BaseProduct p = createProduct("SKU-TC-2");

        var created = stockInBillService.create(new StockInBillCreateRequest(
                wh.getId(),
                "tc stock-in",
                List.of(new StockInBillLineRequest(p.getId(), new BigDecimal("2.000")))), "tester");
        stockInBillService.execute(created.id());

        var r1 = stockInBillService.reverse(created.id(), "tester");
        var r2 = stockInBillService.reverse(created.id(), "tester");

        assertNotNull(r1.reversalBillId());
        assertEquals(r1.reversalBillId(), r2.reversalBillId());
        assertEquals(r1.reversalBillNo(), r2.reversalBillNo());

        WmsStock stock = stockRepository.findFirstByWarehouseIdAndProductId(wh.getId(), p.getId()).orElseThrow();
        assertTrue(stock.getStockQty().compareTo(BigDecimal.ZERO) == 0);
        assertEquals(1L, stockLogRepository.countByBizNoAndBizType(r1.reversalBillNo(), "REVERSAL_IN"));
    }

    @Test
    @Transactional
    void stockOutExecute_failsWhenInsufficientStock() {
        // 用例 3：盘点出库单执行时库存不足应失败（400）
        BaseWarehouse wh = createWarehouse("WH-TC-3");
        BaseProduct p = createProduct("SKU-TC-3");

        upsertStock(wh.getId(), p.getId(), new BigDecimal("1.000"));

        var created = stockOutBillService.create(new StockOutBillCreateRequest(
                wh.getId(),
                "tc stock-out",
                List.of(new StockOutBillLineRequest(p.getId(), new BigDecimal("2.000")))), "tester");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> stockOutBillService.execute(created.id()));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    @Transactional
    void checkBillExecute_createsAdjustBills_andIsIdempotent() {
        // 用例 4：盘点单（counted_qty 实盘数量）执行
        // - 账面 10，实盘 8 => diff = -2：应生成“盘点调整出库单”，扣减库存并写 CHECK_ADJUST_OUT 流水
        // - 同时在盘点明细上写入 book_qty/diff_qty（用于审计与追溯）
        // - 重复 execute：返回同一张调整单（幂等）
        BaseWarehouse wh = createWarehouse("WH-TC-4");
        BaseProduct p = createProduct("SKU-TC-4");

        upsertStock(wh.getId(), p.getId(), new BigDecimal("10.000"));

        var created = checkBillService.create(new WmsCheckBillCreateRequest(
                wh.getId(),
                "tc check",
                List.of(new WmsCheckBillLineRequest(p.getId(), new BigDecimal("8.000")))), "tester");

        var exec1 = checkBillService.execute(created.id(), "tester");
        assertEquals(created.id(), exec1.checkBillId());
        assertNotNull(exec1.checkBillNo());
        assertNull(exec1.stockInBillNo());
        assertNotNull(exec1.stockOutBillNo());

        WmsStock stock = stockRepository.findFirstByWarehouseIdAndProductId(wh.getId(), p.getId()).orElseThrow();
        assertTrue(stock.getStockQty().compareTo(new BigDecimal("8.000")) == 0);
        assertEquals(1L, stockLogRepository.countByBizNoAndBizType(exec1.stockOutBillNo(), "CHECK_ADJUST_OUT"));

        var d = checkBillDetailRepository.findByBillId(created.id()).get(0);
        assertTrue(d.getBookQty().compareTo(new BigDecimal("10.000")) == 0);
        assertTrue(d.getDiffQty().compareTo(new BigDecimal("-2.000")) == 0);

        var exec2 = checkBillService.execute(created.id(), "tester");
        assertEquals(exec1.stockOutBillId(), exec2.stockOutBillId());
        assertEquals(exec1.stockOutBillNo(), exec2.stockOutBillNo());
    }

    private BaseWarehouse createWarehouse(String code) {
        // 测试辅助：创建启用仓库（deleted=0/status=1）
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
        // 测试辅助：创建启用商品（deleted=0/status=1）
        BaseProduct p = new BaseProduct();
        p.setProductCode(code);
        p.setProductName(code);
        p.setUnit("个");
        p.setPurchasePrice(new BigDecimal("1.00"));
        p.setSalePrice(new BigDecimal("2.00"));
        p.setStatus(1);
        p.setDeleted(0);
        p.setCreateTime(LocalDateTime.now());
        p.setUpdateTime(LocalDateTime.now());
        return productRepository.saveAndFlush(p);
    }

    private void upsertStock(Long warehouseId, Long productId, BigDecimal stockQty) {
        // 测试辅助：直接写入/覆盖 wms_stock 的库存数量（用于构造“库存不足/盘点差异”等场景）
        WmsStock stock = stockRepository.findFirstByWarehouseIdAndProductId(warehouseId, productId).orElse(null);
        if (stock == null) {
            stock = new WmsStock();
            stock.setWarehouseId(warehouseId);
            stock.setProductId(productId);
            stock.setLockedQty(BigDecimal.ZERO);
            stock.setVersion(0);
        }
        stock.setStockQty(stockQty);
        stock.setUpdateTime(LocalDateTime.now());
        stockRepository.saveAndFlush(stock);
    }
}
