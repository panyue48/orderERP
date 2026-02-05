package com.ordererp.backend.purchase.controller;

import com.ordererp.backend.common.dto.PageResponse;
import com.ordererp.backend.purchase.dto.PurApBillCreateRequest;
import com.ordererp.backend.purchase.dto.PurApBillDetailResponse;
import com.ordererp.backend.purchase.dto.PurApBillResponse;
import com.ordererp.backend.purchase.dto.PurApInvoiceCreateRequest;
import com.ordererp.backend.purchase.dto.PurApInvoiceResponse;
import com.ordererp.backend.purchase.dto.PurApPaymentCreateRequest;
import com.ordererp.backend.purchase.dto.PurApPaymentResponse;
import com.ordererp.backend.purchase.service.PurApBillService;
import com.ordererp.backend.system.security.SysUserDetails;
import jakarta.validation.Valid;
import java.time.LocalDate;
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
@RequestMapping("/api/purchase/ap-bills")
public class PurApBillController {
    private final PurApBillService billService;

    public PurApBillController(PurApBillService billService) {
        this.billService = billService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('pur:ap:view')")
    public PageResponse<PurApBillResponse> page(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long supplierId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        Pageable pageable = PageRequest.of(page, size);
        Page<PurApBillResponse> res = billService.page(keyword, supplierId, startDate, endDate, pageable);
        return PageResponse.from(res);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('pur:ap:view')")
    public PurApBillDetailResponse detail(@PathVariable Long id) {
        return billService.detail(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('pur:ap:add')")
    public PurApBillResponse create(@Valid @RequestBody PurApBillCreateRequest request, Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return billService.create(request, user.getUsername());
    }

    @PostMapping("/{id}/audit")
    @PreAuthorize("hasAuthority('pur:ap:audit')")
    public PurApBillResponse audit(@PathVariable Long id, Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return billService.audit(id, user.getUsername());
    }

    @PostMapping("/{id}/regenerate")
    @PreAuthorize("hasAuthority('pur:ap:regen')")
    public PurApBillResponse regenerate(@PathVariable Long id, Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return billService.regenerate(id, user.getUsername());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('pur:ap:cancel')")
    public PurApBillResponse cancel(@PathVariable Long id, Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return billService.cancel(id, user.getUsername());
    }

    @PostMapping("/{id}/payments")
    @PreAuthorize("hasAuthority('pur:ap:pay')")
    public PurApPaymentResponse addPayment(@PathVariable Long id, @Valid @RequestBody PurApPaymentCreateRequest request,
            Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return billService.addPayment(id, request, user.getUsername());
    }

    @PostMapping("/{id}/payments/{paymentId}/cancel")
    @PreAuthorize("hasAuthority('pur:ap:pay')")
    public PurApPaymentResponse cancelPayment(@PathVariable Long id, @PathVariable Long paymentId, Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return billService.cancelPayment(id, paymentId, user.getUsername());
    }

    @PostMapping("/{id}/invoices")
    @PreAuthorize("hasAuthority('pur:ap:invoice')")
    public PurApInvoiceResponse addInvoice(@PathVariable Long id, @Valid @RequestBody PurApInvoiceCreateRequest request,
            Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return billService.addInvoice(id, request, user.getUsername());
    }

    @PostMapping("/{id}/invoices/{invoiceId}/cancel")
    @PreAuthorize("hasAuthority('pur:ap:invoice')")
    public PurApInvoiceResponse cancelInvoice(@PathVariable Long id, @PathVariable Long invoiceId, Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return billService.cancelInvoice(id, invoiceId, user.getUsername());
    }
}
