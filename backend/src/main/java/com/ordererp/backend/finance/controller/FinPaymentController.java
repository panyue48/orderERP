package com.ordererp.backend.finance.controller;

import com.ordererp.backend.common.dto.PageResponse;
import com.ordererp.backend.finance.dto.FinPaymentResponse;
import com.ordererp.backend.finance.repository.FinPaymentRepository;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finance/payments")
public class FinPaymentController {
    private final FinPaymentRepository paymentRepository;

    public FinPaymentController(FinPaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('fin:payment:view')")
    public PageResponse<FinPaymentResponse> page(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 200)));
        Page<FinPaymentResponse> res = paymentRepository.pageRows(keyword == null ? null : keyword.trim(), type, partnerId, accountId, startDate, endDate,
                        pageable)
                .map(r -> new FinPaymentResponse(
                        r.getId(),
                        r.getPayNo(),
                        r.getType(),
                        r.getPartnerId(),
                        r.getPartnerName(),
                        r.getAccountId(),
                        r.getAccountName(),
                        r.getAmount(),
                        r.getBizType(),
                        r.getBizId(),
                        r.getBizNo(),
                        r.getPayDate(),
                        r.getMethod(),
                        r.getRemark(),
                        r.getStatus(),
                        r.getCreateBy(),
                        r.getCreateTime(),
                        r.getCancelBy(),
                        r.getCancelTime()));
        return PageResponse.from(res);
    }
}

