package com.ordererp.backend.finance.service;

import com.ordererp.backend.finance.dto.FinManualPaymentCreateRequest;
import com.ordererp.backend.finance.dto.FinManualPaymentResponse;
import com.ordererp.backend.finance.entity.FinManualPayment;
import com.ordererp.backend.finance.repository.FinManualPaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FinManualPaymentService {
    public static final int STATUS_COMPLETED = 1;
    public static final int STATUS_CANCELED = 9;

    private final FinManualPaymentRepository manualRepository;
    private final FinPaymentService paymentService;

    public FinManualPaymentService(FinManualPaymentRepository manualRepository, FinPaymentService paymentService) {
        this.manualRepository = manualRepository;
        this.paymentService = paymentService;
    }

    @Transactional
    public FinManualPaymentResponse create(FinManualPaymentCreateRequest request, String operator) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        Integer type = request.type();
        if (!Objects.equals(type, FinPaymentService.TYPE_RECEIPT) && !Objects.equals(type, FinPaymentService.TYPE_PAYMENT)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "type must be 1(receipt) or 2(payment)");
        }

        BigDecimal amt = safeMoney(request.amount());
        if (amt.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be > 0");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDate payDate = request.payDate() == null ? LocalDate.now() : request.payDate();

        FinManualPayment m = new FinManualPayment();
        m.setManualNo(generateManualNo(type));
        m.setType(type);
        m.setPartnerId(request.partnerId());
        m.setAccountId(request.accountId() == null ? null : request.accountId());
        m.setAmount(amt);
        m.setPayDate(payDate);
        m.setMethod(trimToNull(request.method()));
        m.setBizNo(trimToNull(request.bizNo()));
        m.setRemark(trimToNull(request.remark()));
        m.setStatus(STATUS_COMPLETED);
        m.setCreateBy(trimToNull(operator));
        m.setCreateTime(now);

        try {
            m = manualRepository.saveAndFlush(m);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "手工收付款单号冲突，请重试");
        }

        int bizType = Objects.equals(type, FinPaymentService.TYPE_RECEIPT)
                ? FinPaymentService.BIZ_TYPE_MANUAL_RECEIPT
                : FinPaymentService.BIZ_TYPE_MANUAL_PAYMENT;
        paymentService.recordPayment(
                m.getManualNo(),
                type,
                m.getPartnerId(),
                request.accountId(),
                amt,
                bizType,
                m.getId(),
                m.getBizNo() == null ? m.getManualNo() : m.getBizNo(),
                payDate,
                m.getMethod(),
                m.getRemark(),
                operator);

        return toResponse(m);
    }

    @Transactional
    public FinManualPaymentResponse cancel(Long id, String operator) {
        FinManualPayment m = manualRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "手工收付款记录不存在"));
        if (Objects.equals(m.getStatus(), STATUS_CANCELED)) return toResponse(m);
        if (!Objects.equals(m.getStatus(), STATUS_COMPLETED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "手工收付款状态不允许作废");
        }

        int bizType = Objects.equals(m.getType(), FinPaymentService.TYPE_RECEIPT)
                ? FinPaymentService.BIZ_TYPE_MANUAL_RECEIPT
                : FinPaymentService.BIZ_TYPE_MANUAL_PAYMENT;
        paymentService.cancelByBiz(bizType, m.getId(), operator);

        m.setStatus(STATUS_CANCELED);
        m.setCancelBy(trimToNull(operator));
        m.setCancelTime(LocalDateTime.now());
        manualRepository.save(m);
        return toResponse(m);
    }

    private static FinManualPaymentResponse toResponse(FinManualPayment m) {
        return new FinManualPaymentResponse(
                m.getId(),
                m.getManualNo(),
                m.getType(),
                m.getPartnerId(),
                m.getAccountId(),
                m.getAmount(),
                m.getPayDate(),
                m.getMethod(),
                m.getBizNo(),
                m.getRemark(),
                m.getStatus(),
                m.getCreateBy(),
                m.getCreateTime(),
                m.getCancelBy(),
                m.getCancelTime());
    }

    private static BigDecimal safeMoney(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String s = value.trim();
        return s.isEmpty() ? null : s;
    }

    private static String generateManualNo(int type) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int rand = (int) (Math.random() * 9000) + 1000;
        return (type == FinPaymentService.TYPE_RECEIPT ? "MREC" : "MPAY") + ts + "-" + rand;
    }
}

