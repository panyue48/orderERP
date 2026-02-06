package com.ordererp.backend.sales.controller;

import com.ordererp.backend.common.dto.PageResponse;
import com.ordererp.backend.sales.dto.SalReturnCreateRequest;
import com.ordererp.backend.sales.dto.SalReturnDetailResponse;
import com.ordererp.backend.sales.dto.SalReturnExecuteResponse;
import com.ordererp.backend.sales.dto.SalReturnResponse;
import com.ordererp.backend.sales.service.SalReturnService;
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
            @RequestParam(required = false) String keyword) {
        Pageable pageable = PageRequest.of(page, size);
        Page<SalReturnResponse> res = returnService.page(keyword, pageable);
        return PageResponse.from(res);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sal:return:view')")
    public SalReturnDetailResponse detail(@PathVariable Long id) {
        return returnService.detail(id);
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

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('sal:return:cancel')")
    public SalReturnResponse cancel(@PathVariable Long id, Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return returnService.cancel(id, user.getUsername());
    }
}

