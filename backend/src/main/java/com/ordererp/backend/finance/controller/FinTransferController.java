package com.ordererp.backend.finance.controller;

import com.ordererp.backend.finance.dto.FinTransferCreateRequest;
import com.ordererp.backend.finance.dto.FinTransferResponse;
import com.ordererp.backend.finance.service.FinTransferService;
import com.ordererp.backend.system.security.SysUserDetails;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finance/transfers")
public class FinTransferController {
    private final FinTransferService transferService;

    public FinTransferController(FinTransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('fin:transfer:add')")
    public FinTransferResponse create(@Valid @RequestBody FinTransferCreateRequest request, Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return transferService.create(request, user.getUsername());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('fin:transfer:cancel')")
    public FinTransferResponse cancel(@PathVariable Long id, Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return transferService.cancel(id, user.getUsername());
    }
}

