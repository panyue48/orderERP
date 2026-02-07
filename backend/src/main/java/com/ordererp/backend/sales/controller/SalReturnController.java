package com.ordererp.backend.sales.controller;

import com.ordererp.backend.common.dto.PageResponse;
import com.ordererp.backend.sales.dto.SalReturnCreateRequest;
import com.ordererp.backend.sales.dto.SalReturnDetailResponse;
import com.ordererp.backend.sales.dto.SalReturnExecuteResponse;
import com.ordererp.backend.sales.dto.SalReturnQcRejectRequest;
import com.ordererp.backend.sales.dto.SalReturnItemResponse;
import com.ordererp.backend.sales.dto.SalReturnResponse;
import com.ordererp.backend.sales.service.SalReturnService;
import com.ordererp.backend.system.security.SysUserDetails;
import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/api/sales/returns")
public class SalReturnController {
    private final SalReturnService returnService;

    public SalReturnController(SalReturnService returnService) {
        this.returnService = returnService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('sal:return:view')")
    public PageResponse<SalReturnResponse> page(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            Authentication authentication) {
        Pageable pageable = PageRequest.of(page, size);
        Page<SalReturnResponse> res = returnService.page(keyword, pageable);
        if (!canViewPrice(authentication)) {
            res = res.map(SalReturnController::maskPrice);
        }
        return PageResponse.from(res);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sal:return:view')")
    public SalReturnDetailResponse detail(@PathVariable Long id, Authentication authentication) {
        SalReturnDetailResponse res = returnService.detail(id);
        return canViewPrice(authentication) ? res : maskPrice(res);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('sal:return:add')")
    public SalReturnResponse create(@Valid @RequestBody SalReturnCreateRequest request, Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return returnService.create(request, user.getUsername());
    }

    @PostMapping("/{id}/audit")
    @PreAuthorize("hasAuthority('sal:return:audit')")
    public SalReturnResponse audit(@PathVariable Long id, Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return returnService.audit(id, user.getUsername());
    }

    @PostMapping("/{id}/execute")
    @PreAuthorize("hasAuthority('sal:return:execute')")
    public SalReturnExecuteResponse execute(@PathVariable Long id, Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return returnService.execute(id, user.getUsername());
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("hasAuthority('sal:return:receive')")
    public SalReturnResponse receive(@PathVariable Long id, Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return returnService.receive(id, user.getUsername());
    }

    @PostMapping("/{id}/qc-reject")
    @PreAuthorize("hasAuthority('sal:return:qc-reject')")
    public SalReturnResponse qcReject(@PathVariable Long id, @Valid @RequestBody SalReturnQcRejectRequest request, Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return returnService.qcReject(id, user.getUsername(), request.disposition(), request.remark());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('sal:return:cancel')")
    public SalReturnResponse cancel(@PathVariable Long id, Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return returnService.cancel(id, user.getUsername());
    }

    private static boolean canViewPrice(Authentication authentication) {
        if (authentication == null) return false;
        for (GrantedAuthority ga : authentication.getAuthorities()) {
            String a = ga.getAuthority();
            if ("sal:price:view".equals(a) || "sal:price:edit".equals(a)) return true;
        }
        return false;
    }

    private static SalReturnResponse maskPrice(SalReturnResponse r) {
        if (r == null) return null;
        return new SalReturnResponse(
                r.id(),
                r.returnNo(),
                r.customerId(),
                r.customerCode(),
                r.customerName(),
                r.warehouseId(),
                r.warehouseName(),
                r.shipId(),
                r.shipNo(),
                r.orderId(),
                r.orderNo(),
                r.returnDate(),
                r.totalQty(),
                null,
                r.status(),
                r.remark(),
                r.wmsBillId(),
                r.wmsBillNo(),
                r.createBy(),
                r.createTime(),
                r.auditBy(),
                r.auditTime(),
                r.receiveBy(),
                r.receiveTime(),
                r.qcBy(),
                r.qcTime(),
                r.qcDisposition(),
                r.qcRemark(),
                r.executeBy(),
                r.executeTime());
    }

    private static SalReturnDetailResponse maskPrice(SalReturnDetailResponse d) {
        if (d == null) return null;
        var items = d.items() == null ? List.<SalReturnItemResponse>of() : d.items().stream().map(SalReturnController::maskPrice).toList();
        return new SalReturnDetailResponse(maskPrice(d.header()), items);
    }

    private static SalReturnItemResponse maskPrice(SalReturnItemResponse it) {
        if (it == null) return null;
        return new SalReturnItemResponse(
                it.id(),
                it.shipDetailId(),
                it.orderDetailId(),
                it.productId(),
                it.productCode(),
                it.productName(),
                it.unit(),
                null,
                it.qty(),
                null);
    }
}
