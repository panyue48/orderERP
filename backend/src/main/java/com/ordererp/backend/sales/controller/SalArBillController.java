package com.ordererp.backend.sales.controller;

import com.ordererp.backend.common.dto.PageResponse;
import com.ordererp.backend.sales.dto.SalArBillCreateRequest;
import com.ordererp.backend.sales.dto.SalArBillDetailResponse;
import com.ordererp.backend.sales.dto.SalArBillResponse;
import com.ordererp.backend.sales.dto.SalArInvoiceCreateRequest;
import com.ordererp.backend.sales.dto.SalArInvoiceResponse;
import com.ordererp.backend.sales.dto.SalArReceiptCreateRequest;
import com.ordererp.backend.sales.dto.SalArReceiptResponse;
import com.ordererp.backend.sales.service.SalArBillService;
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
@RequestMapping("/api/sales/ar-bills")
public class SalArBillController {
    private final SalArBillService billService;

    public SalArBillController(SalArBillService billService) {
        this.billService = billService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('sal:ar:view')")
    public PageResponse<SalArBillResponse> page(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        Pageable pageable = PageRequest.of(page, size);
        Page<SalArBillResponse> res = billService.page(keyword, customerId, startDate, endDate, pageable);
        return PageResponse.from(res);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sal:ar:view')")
    public SalArBillDetailResponse detail(@PathVariable Long id) {
        return billService.detail(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('sal:ar:add')")
    public SalArBillResponse create(@Valid @RequestBody SalArBillCreateRequest request, Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return billService.create(request, user.getUsername());
    }

    @PostMapping("/{id}/audit")
    @PreAuthorize("hasAuthority('sal:ar:audit')")
    public SalArBillResponse audit(@PathVariable Long id, Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return billService.audit(id, user.getUsername());
    }

    @PostMapping("/{id}/regenerate")
    @PreAuthorize("hasAuthority('sal:ar:regen')")
    public SalArBillResponse regenerate(@PathVariable Long id, Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return billService.regenerate(id, user.getUsername());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('sal:ar:cancel')")
    public SalArBillResponse cancel(@PathVariable Long id, Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return billService.cancel(id, user.getUsername());
    }

    @PostMapping("/{id}/receipts")
    @PreAuthorize("hasAuthority('sal:ar:recv')")
    public SalArReceiptResponse addReceipt(@PathVariable Long id, @Valid @RequestBody SalArReceiptCreateRequest request,
            Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return billService.addReceipt(id, request, user.getUsername());
    }

    @PostMapping("/{id}/receipts/{receiptId}/cancel")
    @PreAuthorize("hasAuthority('sal:ar:recv')")
    public SalArReceiptResponse cancelReceipt(@PathVariable Long id, @PathVariable Long receiptId, Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return billService.cancelReceipt(id, receiptId, user.getUsername());
    }

    @PostMapping("/{id}/invoices")
    @PreAuthorize("hasAuthority('sal:ar:invoice')")
    public SalArInvoiceResponse addInvoice(@PathVariable Long id, @Valid @RequestBody SalArInvoiceCreateRequest request,
            Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return billService.addInvoice(id, request, user.getUsername());
    }

    @PostMapping("/{id}/invoices/{invoiceId}/cancel")
    @PreAuthorize("hasAuthority('sal:ar:invoice')")
    public SalArInvoiceResponse cancelInvoice(@PathVariable Long id, @PathVariable Long invoiceId, Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return billService.cancelInvoice(id, invoiceId, user.getUsername());
    }
}

