package com.ordererp.backend.finance.service;

import com.ordererp.backend.finance.dto.FinTransferCreateRequest;
import com.ordererp.backend.finance.dto.FinTransferResponse;
import com.ordererp.backend.finance.entity.FinTransfer;
import com.ordererp.backend.finance.repository.FinTransferRepository;
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
public class FinTransferService {
    public static final int STATUS_COMPLETED = 1;
    public static final int STATUS_CANCELED = 9;

    private final FinTransferRepository transferRepository;
    private final FinPaymentService paymentService;

    public FinTransferService(FinTransferRepository transferRepository, FinPaymentService paymentService) {
        this.transferRepository = transferRepository;
        this.paymentService = paymentService;
    }

    @Transactional
    public FinTransferResponse create(FinTransferCreateRequest request, String operator) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        if (request.fromAccountId() == null || request.toAccountId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fromAccountId/toAccountId is required");
        }
        if (Objects.equals(request.fromAccountId(), request.toAccountId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "转出/转入账户不能相同");
        }
        BigDecimal amt = safeMoney(request.amount());
        if (amt.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be > 0");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDate date = request.transferDate() == null ? LocalDate.now() : request.transferDate();

        FinTransfer t = new FinTransfer();
        t.setTransferNo(generateTransferNo());
        t.setFromAccountId(request.fromAccountId());
        t.setToAccountId(request.toAccountId());
        t.setAmount(amt);
        t.setTransferDate(date);
        t.setRemark(trimToNull(request.remark()));
        t.setStatus(STATUS_COMPLETED);
        t.setCreateBy(trimToNull(operator));
        t.setCreateTime(now);

        try {
            t = transferRepository.saveAndFlush(t);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "调拨单号冲突，请重试");
        }

        // Out: fromAccount decreases
        paymentService.recordPayment(
                generateTransferPayNo("TO"),
                FinPaymentService.TYPE_PAYMENT,
                null,
                request.fromAccountId(),
                amt,
                FinPaymentService.BIZ_TYPE_TRANSFER_OUT,
                t.getId(),
                t.getTransferNo(),
                date,
                "transfer",
                t.getRemark(),
                operator);

        // In: toAccount increases
        paymentService.recordPayment(
                generateTransferPayNo("TI"),
                FinPaymentService.TYPE_RECEIPT,
                null,
                request.toAccountId(),
                amt,
                FinPaymentService.BIZ_TYPE_TRANSFER_IN,
                t.getId(),
                t.getTransferNo(),
                date,
                "transfer",
                t.getRemark(),
                operator);

        return toResponse(t);
    }

    @Transactional
    public FinTransferResponse cancel(Long id, String operator) {
        FinTransfer t = transferRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "调拨记录不存在"));
        if (Objects.equals(t.getStatus(), STATUS_CANCELED)) return toResponse(t);
        if (!Objects.equals(t.getStatus(), STATUS_COMPLETED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "调拨状态不允许作废");
        }

        paymentService.cancelByBiz(FinPaymentService.BIZ_TYPE_TRANSFER_OUT, t.getId(), operator);
        paymentService.cancelByBiz(FinPaymentService.BIZ_TYPE_TRANSFER_IN, t.getId(), operator);

        t.setStatus(STATUS_CANCELED);
        t.setCancelBy(trimToNull(operator));
        t.setCancelTime(LocalDateTime.now());
        transferRepository.save(t);
        return toResponse(t);
    }

    private static FinTransferResponse toResponse(FinTransfer t) {
        return new FinTransferResponse(
                t.getId(),
                t.getTransferNo(),
                t.getFromAccountId(),
                t.getToAccountId(),
                t.getAmount(),
                t.getTransferDate(),
                t.getRemark(),
                t.getStatus(),
                t.getCreateBy(),
                t.getCreateTime(),
                t.getCancelBy(),
                t.getCancelTime());
    }

    private static BigDecimal safeMoney(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String s = value.trim();
        return s.isEmpty() ? null : s;
    }

    private static String generateTransferNo() {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int rand = (int) (Math.random() * 9000) + 1000;
        return "TRF" + ts + "-" + rand;
    }

    private static String generateTransferPayNo(String prefix) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int rand = (int) (Math.random() * 9000) + 1000;
        return prefix + ts + "-" + rand;
    }
}

