package com.ordererp.backend.purchase.controller;

import com.ordererp.backend.common.dto.PageResponse;
import com.ordererp.backend.purchase.dto.PurOrderCreateRequest;
import com.ordererp.backend.purchase.dto.PurOrderDetailResponse;
import com.ordererp.backend.purchase.dto.PurInboundCreateRequest;
import com.ordererp.backend.purchase.dto.PurInboundExecuteResponse;
import com.ordererp.backend.purchase.dto.PurOrderInboundRequest;
import com.ordererp.backend.purchase.dto.PurOrderInboundResponse;
import com.ordererp.backend.purchase.dto.PurOrderResponse;
import com.ordererp.backend.purchase.service.PurInboundService;
import com.ordererp.backend.purchase.service.PurOrderService;
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
@RequestMapping("/api/purchase/orders")
public class PurOrderController {
    private final PurOrderService orderService;
    private final PurInboundService inboundService;

    public PurOrderController(PurOrderService orderService, PurInboundService inboundService) {
        this.orderService = orderService;
        this.inboundService = inboundService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('pur:order:view')")
    public PageResponse<PurOrderResponse> page(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        Pageable pageable = PageRequest.of(page, size);
        Page<PurOrderResponse> res = orderService.page(keyword, pageable);
        return PageResponse.from(res);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('pur:order:view')")
    public PurOrderDetailResponse detail(@PathVariable Long id) {
        return orderService.detail(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('pur:order:add')")
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
    @PreAuthorize("hasAuthority('pur:order:inbound')")
    public PurInboundExecuteResponse batchInbound(@PathVariable Long id, @RequestBody PurInboundCreateRequest request,
            Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return inboundService.createAndExecuteFromOrder(id, request, user.getUsername());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('pur:order:cancel')")
    public PurOrderResponse cancel(@PathVariable Long id, Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return orderService.cancel(id, user.getUsername());
    }
}
