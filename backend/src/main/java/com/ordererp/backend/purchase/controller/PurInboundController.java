package com.ordererp.backend.purchase.controller;

import com.ordererp.backend.common.dto.PageResponse;
import com.ordererp.backend.purchase.dto.PurInboundDetailResponse;
import com.ordererp.backend.purchase.dto.PurInboundExecuteResponse;
import com.ordererp.backend.purchase.dto.PurInboundIqcRequest;
import com.ordererp.backend.purchase.dto.PurInboundNewOrderRequest;
import com.ordererp.backend.purchase.dto.PurInboundResponse;
import com.ordererp.backend.purchase.service.PurInboundService;
import com.ordererp.backend.system.security.SysUserDetails;
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
@RequestMapping("/api/purchase/inbounds")
public class PurInboundController {
    private final PurInboundService inboundService;

    public PurInboundController(PurInboundService inboundService) {
        this.inboundService = inboundService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('pur:inbound:view')")
    public PageResponse<PurInboundResponse> page(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long orderId) {
        Pageable pageable = PageRequest.of(page, size);
        Page<PurInboundResponse> res = inboundService.page(keyword, orderId, pageable);
        return PageResponse.from(res);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('pur:inbound:view')")
    public PurInboundDetailResponse detail(@PathVariable Long id) {
        return inboundService.detail(id);
    }

    /**
     * 新建采购入库单（同时创建采购订单并执行入库）。
     */
    @PostMapping
    @PreAuthorize("hasAuthority('pur:inbound:add')")
    public PurInboundExecuteResponse create(@Valid @RequestBody PurInboundNewOrderRequest request, Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return inboundService.createAndExecuteNewOrder(request, user.getUsername());
    }

    @PostMapping("/{id}/iqc-pass")
    @PreAuthorize("hasAuthority('pur:inbound:iqc')")
    public PurInboundExecuteResponse iqcPass(@PathVariable Long id, @RequestBody(required = false) PurInboundIqcRequest request,
            Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return inboundService.iqcPassAndExecute(id, request == null ? null : request.remark(), user.getUsername());
    }

    @PostMapping("/{id}/iqc-reject")
    @PreAuthorize("hasAuthority('pur:inbound:iqc')")
    public PurInboundResponse iqcReject(@PathVariable Long id, @RequestBody(required = false) PurInboundIqcRequest request,
            Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return inboundService.iqcReject(id, request == null ? null : request.remark(), user.getUsername());
    }
}
