package com.ordererp.backend.purchase.controller;

import com.ordererp.backend.common.dto.PageResponse;
import com.ordererp.backend.purchase.dto.PurOrderCreateRequest;
import com.ordererp.backend.purchase.dto.PurOrderDetailResponse;
import com.ordererp.backend.purchase.dto.PurInboundCreateRequest;
import com.ordererp.backend.purchase.dto.PurInboundExecuteResponse;
import com.ordererp.backend.purchase.dto.PurOrderInboundRequest;
import com.ordererp.backend.purchase.dto.PurOrderInboundResponse;
import com.ordererp.backend.purchase.dto.PurOrderOptionResponse;
import com.ordererp.backend.purchase.dto.PurOrderResponse;
import com.ordererp.backend.purchase.dto.PurPendingQcSummaryResponse;
import com.ordererp.backend.purchase.excel.PurOrderExcelRow;
import com.ordererp.backend.purchase.service.PurInboundService;
import com.ordererp.backend.purchase.service.PurOrderExcelService;
import com.ordererp.backend.purchase.service.PurOrderService;
import com.ordererp.backend.system.security.SysUserDetails;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestPart;
import com.alibaba.excel.EasyExcel;
import com.ordererp.backend.common.dto.ImportResult;
import com.ordererp.backend.common.util.ExcelHttpUtil;

@RestController
@RequestMapping("/api/purchase/orders")
public class PurOrderController {
    private final PurOrderService orderService;
    private final PurInboundService inboundService;
    private final PurOrderExcelService orderExcelService;

    public PurOrderController(PurOrderService orderService, PurInboundService inboundService, PurOrderExcelService orderExcelService) {
        this.orderService = orderService;
        this.inboundService = inboundService;
        this.orderExcelService = orderExcelService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('pur:order:view')")
    public PageResponse<PurOrderResponse> page(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            Authentication authentication) {
        Pageable pageable = PageRequest.of(page, size);
        Page<PurOrderResponse> res = orderService.page(keyword, pageable);
        if (!canViewPrice(authentication)) {
            res = res.map(PurOrderController::maskPrice);
        }
        return PageResponse.from(res);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('pur:order:view')")
    public PurOrderDetailResponse detail(@PathVariable Long id, Authentication authentication) {
        PurOrderDetailResponse res = orderService.detail(id);
        return canViewPrice(authentication) ? res : maskPrice(res);
    }

    @GetMapping("/options")
    @PreAuthorize("hasAuthority('pur:inbound:add')")
    public List<PurOrderOptionResponse> options(@RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "50") int limit) {
        return orderService.options(keyword, limit);
    }

    @GetMapping("/{id}/pending-qc-summary")
    @PreAuthorize("hasAuthority('pur:inbound:add')")
    public PurPendingQcSummaryResponse pendingQcSummary(@PathVariable Long id) {
        return inboundService.pendingQcSummary(id);
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('pur:order:export') and hasAnyAuthority('pur:price:view','pur:price:edit')")
    public void export(@RequestParam(required = false) String keyword, HttpServletResponse response) throws IOException {
        ExcelHttpUtil.prepareXlsxResponse(response, "purchase-orders.xlsx");

        int page = 0;
        int size = 2_000;
        try (var writer = EasyExcel.write(response.getOutputStream(), PurOrderExcelRow.class).build()) {
            var sheet = EasyExcel.writerSheet("PurchaseOrders").build();
            while (true) {
                Page<PurOrderExcelRow> res = orderExcelService.exportOrders(keyword, PageRequest.of(page, size));
                if (res.isEmpty()) break;
                writer.write(res.getContent(), sheet);
                if (!res.hasNext()) break;
                page++;
            }
        }
    }

    @GetMapping("/import-template")
    @PreAuthorize("hasAuthority('pur:order:export') and hasAnyAuthority('pur:price:view','pur:price:edit')")
    public void importTemplate(HttpServletResponse response) throws IOException {
        ExcelHttpUtil.prepareXlsxResponse(response, "purchase-orders-import-template.xlsx");
        PurOrderExcelRow sample = new PurOrderExcelRow();
        sample.setOrderNo("PO20260205-1001");
        sample.setSupplierCode("SUP-001");
        sample.setSupplierName("示例供应商（可不填）");
        sample.setOrderDate(java.time.LocalDate.now());
        sample.setStatus(2);
        sample.setProductCode("SKU-001");
        sample.setProductName("示例商品（可不填）");
        sample.setUnit("个");
        sample.setPrice(new java.math.BigDecimal("10.50"));
        sample.setQty(new java.math.BigDecimal("100"));
        sample.setAmount(null);
        sample.setInQty(null);
        sample.setRemark("导入示例：同 orderNo 的多行会合并为同一采购单");
        EasyExcel.write(response.getOutputStream(), PurOrderExcelRow.class).sheet("Template").doWrite(java.util.List.of(sample));
    }

    @PostMapping("/import")
    @PreAuthorize("hasAuthority('pur:order:import') and hasAuthority('pur:price:edit')")
    public ImportResult importExcel(@RequestPart("file") MultipartFile file, Authentication authentication) throws IOException {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        List<PurOrderExcelRow> rows = EasyExcel.read(file.getInputStream()).head(PurOrderExcelRow.class).sheet().doReadSync();
        return orderExcelService.importOrders(rows, user.getUsername());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('pur:order:add') and hasAuthority('pur:price:edit')")
    public PurOrderResponse create(@Valid @RequestBody PurOrderCreateRequest request, Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return orderService.create(request, user.getUsername());
    }

    @PostMapping("/{id}/audit")
    @PreAuthorize("hasAuthority('pur:order:audit')")
    public PurOrderResponse audit(@PathVariable Long id, Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return orderService.audit(id, user.getUsername());
    }

    @PostMapping("/{id}/inbound")
    @PreAuthorize("hasAuthority('pur:order:inbound')")
    public PurOrderInboundResponse inbound(@PathVariable Long id, @RequestBody PurOrderInboundRequest request,
            Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return orderService.inbound(id, request, user.getUsername());
    }

    /**
     * 采购分批入库（创建入库单并执行）。
     *
     * <p>与 legacy 的 /inbound 区别：/inbound 为“一键全量入库”，只适用于最小闭环；/inbounds 支持分批入库。</p>
     */
    @PostMapping("/{id}/inbounds")
    @PreAuthorize("hasAuthority('pur:inbound:add')")
    public PurInboundExecuteResponse batchInbound(@PathVariable Long id, @RequestBody PurInboundCreateRequest request,
            Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return inboundService.createFromOrder(id, request, user.getUsername());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('pur:order:cancel')")
    public PurOrderResponse cancel(@PathVariable Long id, Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return orderService.cancel(id, user.getUsername());
    }

    private static boolean canViewPrice(Authentication authentication) {
        if (authentication == null) return false;
        for (GrantedAuthority ga : authentication.getAuthorities()) {
            String a = ga.getAuthority();
            if ("pur:price:view".equals(a) || "pur:price:edit".equals(a)) return true;
        }
        return false;
    }

    private static PurOrderResponse maskPrice(PurOrderResponse r) {
        if (r == null) return null;
        return new PurOrderResponse(
                r.id(),
                r.orderNo(),
                r.supplierId(),
                r.supplierCode(),
                r.supplierName(),
                r.orderDate(),
                null,
                null,
                r.status(),
                r.remark(),
                r.createBy(),
                r.createTime(),
                r.auditBy(),
                r.auditTime());
    }

    private static PurOrderDetailResponse maskPrice(PurOrderDetailResponse d) {
        if (d == null) return null;
        var items = d.items() == null ? List.<com.ordererp.backend.purchase.dto.PurOrderItemResponse>of()
                : d.items().stream().map(PurOrderController::maskPrice).toList();
        return new PurOrderDetailResponse(
                d.id(),
                d.orderNo(),
                d.supplierId(),
                d.supplierCode(),
                d.supplierName(),
                d.orderDate(),
                null,
                null,
                d.status(),
                d.remark(),
                d.createBy(),
                d.createTime(),
                d.auditBy(),
                d.auditTime(),
                items);
    }

    private static com.ordererp.backend.purchase.dto.PurOrderItemResponse maskPrice(com.ordererp.backend.purchase.dto.PurOrderItemResponse it) {
        if (it == null) return null;
        return new com.ordererp.backend.purchase.dto.PurOrderItemResponse(
                it.id(),
                it.productId(),
                it.productCode(),
                it.productName(),
                it.unit(),
                null,
                it.qty(),
                null,
                it.inQty());
    }
}
