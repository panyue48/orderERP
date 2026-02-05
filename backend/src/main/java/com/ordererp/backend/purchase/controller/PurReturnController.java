package com.ordererp.backend.purchase.controller;

import com.ordererp.backend.common.dto.PageResponse;
import com.ordererp.backend.purchase.dto.PurReturnCreateRequest;
import com.ordererp.backend.purchase.dto.PurReturnDetailResponse;
import com.ordererp.backend.purchase.dto.PurReturnExecuteResponse;
import com.ordererp.backend.purchase.dto.PurReturnResponse;
import com.ordererp.backend.purchase.service.PurReturnService;
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
            @RequestParam(required = false) String keyword) {
        Pageable pageable = PageRequest.of(page, size);
        Page<PurReturnResponse> res = returnService.page(keyword, pageable);
        return PageResponse.from(res);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('pur:return:view')")
    public PurReturnDetailResponse detail(@PathVariable Long id) {
        return returnService.detail(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('pur:return:add')")
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
}

