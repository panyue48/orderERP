package com.ordererp.backend.purchase.controller;

import com.ordererp.backend.common.dto.PageResponse;
import com.ordererp.backend.purchase.dto.PurReturnCreateRequest;
import com.ordererp.backend.purchase.dto.PurReturnDetailResponse;
import com.ordererp.backend.purchase.dto.PurReturnExecuteResponse;
import com.ordererp.backend.purchase.dto.PurReturnResponse;
import com.ordererp.backend.purchase.service.PurReturnService;
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
@RequestMapping("/api/purchase/returns")
public class PurReturnController {
    private final PurReturnService returnService;

    public PurReturnController(PurReturnService returnService) {
        this.returnService = returnService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('pur:return:view')")
    public PageResponse<PurReturnResponse> page(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            Authentication authentication) {
        Pageable pageable = PageRequest.of(page, size);
        Page<PurReturnResponse> res = returnService.page(keyword, pageable);
        if (!canViewPrice(authentication)) {
            res = res.map(PurReturnController::maskPrice);
        }
        return PageResponse.from(res);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('pur:return:view')")
    public PurReturnDetailResponse detail(@PathVariable Long id, Authentication authentication) {
        PurReturnDetailResponse res = returnService.detail(id);
        return canViewPrice(authentication) ? res : maskPrice(res);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('pur:return:add') and hasAuthority('pur:price:edit')")
    public PurReturnResponse create(@Valid @RequestBody PurReturnCreateRequest request, Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return returnService.create(request, user.getUsername());
    }

    @PostMapping("/{id}/audit")
    @PreAuthorize("hasAuthority('pur:return:audit')")
    public PurReturnResponse audit(@PathVariable Long id, Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return returnService.audit(id, user.getUsername());
    }

    @PostMapping("/{id}/execute")
    @PreAuthorize("hasAuthority('pur:return:execute')")
    public PurReturnExecuteResponse execute(@PathVariable Long id, Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return returnService.execute(id, user.getUsername());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('pur:return:cancel')")
    public PurReturnResponse cancel(@PathVariable Long id, Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return returnService.cancel(id, user.getUsername());
    }

    private static boolean canViewPrice(Authentication authentication) {
        if (authentication == null) return false;
        for (GrantedAuthority ga : authentication.getAuthorities()) {
            String a = ga.getAuthority();
            if ("pur:price:view".equals(a) || "pur:price:edit".equals(a)) return true;
        }
        return false;
    }

    private static PurReturnResponse maskPrice(PurReturnResponse r) {
        if (r == null) return null;
        return new PurReturnResponse(
                r.id(),
                r.returnNo(),
                r.supplierId(),
                r.supplierCode(),
                r.supplierName(),
                r.warehouseId(),
                r.warehouseName(),
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
                r.executeBy(),
                r.executeTime());
    }

    private static PurReturnDetailResponse maskPrice(PurReturnDetailResponse d) {
        if (d == null) return null;
        var items = d.items() == null ? List.<com.ordererp.backend.purchase.dto.PurReturnItemResponse>of()
                : d.items().stream().map(PurReturnController::maskPrice).toList();
        return new PurReturnDetailResponse(maskPrice(d.header()), items);
    }

    private static com.ordererp.backend.purchase.dto.PurReturnItemResponse maskPrice(com.ordererp.backend.purchase.dto.PurReturnItemResponse it) {
        if (it == null) return null;
        return new com.ordererp.backend.purchase.dto.PurReturnItemResponse(
                it.id(),
                it.productId(),
                it.productCode(),
                it.productName(),
                it.unit(),
                null,
                it.qty(),
                null);
    }
}
