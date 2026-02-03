package com.ordererp.backend.wms.controller;

import com.ordererp.backend.common.dto.PageResponse;
import com.ordererp.backend.system.security.SysUserDetails;
import com.ordererp.backend.wms.dto.StockInBillCreateRequest;
import com.ordererp.backend.wms.dto.StockInBillDetailResponse;
import com.ordererp.backend.wms.dto.StockInBillResponse;
import com.ordererp.backend.wms.dto.WmsBillPrecheckResponse;
import com.ordererp.backend.wms.dto.WmsReverseResponse;
import com.ordererp.backend.wms.service.WmsStockInBillService;
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
@RequestMapping("/api/wms/stock-in-bills")
public class WmsStockInBillController {
    private final WmsStockInBillService stockInBillService;

    public WmsStockInBillController(WmsStockInBillService stockInBillService) {
        this.stockInBillService = stockInBillService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('wms:stockin:view')")
    public PageResponse<StockInBillResponse> page(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        Pageable pageable = PageRequest.of(page, size);
        Page<StockInBillResponse> res = stockInBillService.page(keyword, pageable);
        return PageResponse.from(res);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('wms:stockin:view')")
    public StockInBillDetailResponse detail(@PathVariable Long id) {
        return stockInBillService.detail(id);
    }

    @GetMapping("/{id}/precheck")
    @PreAuthorize("hasAuthority('wms:stockin:view')")
    public WmsBillPrecheckResponse precheck(@PathVariable Long id) {
        return stockInBillService.precheckExecute(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('wms:stockin:add')")
    public StockInBillResponse create(@Valid @RequestBody StockInBillCreateRequest request,
            Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return stockInBillService.create(request, user.getUsername());
    }

    @PostMapping("/{id}/execute")
    @PreAuthorize("hasAuthority('wms:stockin:execute')")
    public StockInBillResponse execute(@PathVariable Long id) {
        return stockInBillService.execute(id);
    }

    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAuthority('wms:stockin:reverse')")
    public WmsReverseResponse reverse(@PathVariable Long id, Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return stockInBillService.reverse(id, user.getUsername());
    }
}
