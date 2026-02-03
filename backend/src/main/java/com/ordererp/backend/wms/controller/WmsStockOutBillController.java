package com.ordererp.backend.wms.controller;

import com.ordererp.backend.common.dto.PageResponse;
import com.ordererp.backend.system.security.SysUserDetails;
import com.ordererp.backend.wms.dto.StockOutBillCreateRequest;
import com.ordererp.backend.wms.dto.StockOutBillDetailResponse;
import com.ordererp.backend.wms.dto.StockOutBillResponse;
import com.ordererp.backend.wms.dto.WmsBillPrecheckResponse;
import com.ordererp.backend.wms.dto.WmsReverseResponse;
import com.ordererp.backend.wms.service.WmsStockOutBillService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wms/stock-out-bills")
public class WmsStockOutBillController {
    private final WmsStockOutBillService stockOutBillService;

    public WmsStockOutBillController(WmsStockOutBillService stockOutBillService) {
        this.stockOutBillService = stockOutBillService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('wms:stockout:view')")
    public PageResponse<StockOutBillResponse> page(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        Pageable pageable = PageRequest.of(page, size);
        Page<StockOutBillResponse> res = stockOutBillService.page(keyword, pageable);
        return PageResponse.from(res);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('wms:stockout:view')")
    public StockOutBillDetailResponse detail(@PathVariable Long id) {
        return stockOutBillService.detail(id);
    }

    @GetMapping("/{id}/precheck")
    @PreAuthorize("hasAuthority('wms:stockout:view')")
    public WmsBillPrecheckResponse precheck(@PathVariable Long id) {
        return stockOutBillService.precheckExecute(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('wms:stockout:add')")
    public StockOutBillResponse create(@Valid @RequestBody StockOutBillCreateRequest request,
            Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return stockOutBillService.create(request, user.getUsername());
    }

    @PostMapping("/{id}/execute")
    @PreAuthorize("hasAuthority('wms:stockout:execute')")
    public StockOutBillResponse execute(@PathVariable Long id) {
        return stockOutBillService.execute(id);
    }

    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAuthority('wms:stockout:reverse')")
    public WmsReverseResponse reverse(@PathVariable Long id, Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return stockOutBillService.reverse(id, user.getUsername());
    }
}
