package com.ordererp.backend.finance.controller;

import com.ordererp.backend.finance.dto.FinManualPaymentCreateRequest;
import com.ordererp.backend.finance.dto.FinManualPaymentResponse;
import com.ordererp.backend.finance.service.FinManualPaymentService;
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
@RequestMapping("/api/finance/manual-payments")
public class FinManualPaymentController {
    private final FinManualPaymentService manualPaymentService;

    public FinManualPaymentController(FinManualPaymentService manualPaymentService) {
        this.manualPaymentService = manualPaymentService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('fin:payment:manual')")
    public FinManualPaymentResponse create(@Valid @RequestBody FinManualPaymentCreateRequest request, Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return manualPaymentService.create(request, user.getUsername());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('fin:payment:manual')")
    public FinManualPaymentResponse cancel(@PathVariable Long id, Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return manualPaymentService.cancel(id, user.getUsername());
    }
}

