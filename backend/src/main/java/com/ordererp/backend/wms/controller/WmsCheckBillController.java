package com.ordererp.backend.wms.controller;

import com.ordererp.backend.common.dto.PageResponse;
import com.ordererp.backend.system.security.SysUserDetails;
import com.ordererp.backend.wms.dto.WmsCheckBillCreateRequest;
import com.ordererp.backend.wms.dto.WmsCheckBillDetailResponse;
import com.ordererp.backend.wms.dto.WmsCheckBillResponse;
import com.ordererp.backend.wms.dto.WmsCheckExecuteResponse;
import com.ordererp.backend.wms.service.WmsCheckBillService;
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
@RequestMapping("/api/wms/check-bills")
public class WmsCheckBillController {
    private final WmsCheckBillService checkBillService;

    public WmsCheckBillController(WmsCheckBillService checkBillService) {
        this.checkBillService = checkBillService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('wms:stockin:view')")
    public PageResponse<WmsCheckBillResponse> page(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        Pageable pageable = PageRequest.of(page, size);
        Page<WmsCheckBillResponse> res = checkBillService.page(keyword, pageable);
        return PageResponse.from(res);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('wms:stockin:view')")
    public WmsCheckBillDetailResponse detail(@PathVariable Long id) {
        return checkBillService.detail(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('wms:stockin:add')")
    public WmsCheckBillResponse create(@Valid @RequestBody WmsCheckBillCreateRequest request,
            Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return checkBillService.create(request, user.getUsername());
    }

    @PostMapping("/{id}/execute")
    @PreAuthorize("hasAuthority('wms:stockin:execute')")
    public WmsCheckExecuteResponse execute(@PathVariable Long id, Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return checkBillService.execute(id, user.getUsername());
    }
}

