package com.ordererp.backend.finance.service;

import com.ordererp.backend.finance.entity.FinAccount;
import com.ordererp.backend.finance.entity.FinPayment;
import com.ordererp.backend.finance.repository.FinAccountRepository;
import com.ordererp.backend.finance.repository.FinPaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FinPaymentService {
    public static final int TYPE_RECEIPT = 1;
    public static final int TYPE_PAYMENT = 2;

    public static final int STATUS_COMPLETED = 1;
    public static final int STATUS_CANCELED = 9;

    // biz_type
    public static final int BIZ_TYPE_SALES_AR_RECEIPT = 1;
    public static final int BIZ_TYPE_PURCHASE_AP_PAYMENT = 2;
    public static final int BIZ_TYPE_TRANSFER_OUT = 3;
    public static final int BIZ_TYPE_TRANSFER_IN = 4;
    public static final int BIZ_TYPE_MANUAL_RECEIPT = 5;
    public static final int BIZ_TYPE_MANUAL_PAYMENT = 6;

    private final FinAccountRepository accountRepository;
    private final FinPaymentRepository paymentRepository;
    private final FinAccountService accountService;

    public FinPaymentService(FinAccountRepository accountRepository, FinPaymentRepository paymentRepository, FinAccountService accountService) {
        this.accountRepository = accountRepository;
        this.paymentRepository = paymentRepository;
        this.accountService = accountService;
    }

    @Transactional
    public FinPayment recordPayment(String payNo,
            int type,
            Long partnerId,
            Long accountId,
            BigDecimal amount,
            int bizType,
            Long bizId,
            String bizNo,
            LocalDate payDate,
            String method,
            String remark,
            String operator) {
        if (payNo == null || payNo.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "payNo is required");
        if (bizId == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bizId is required");
        if (partnerId == null && !isPartnerOptionalBizType(bizType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "partnerId is required");
        }
        BigDecimal amt = safeMoney(amount);
        if (amt.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be > 0");
        }
        if (type != TYPE_RECEIPT && type != TYPE_PAYMENT) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid payment type");
        }

        FinPayment existing = paymentRepository.findFirstByBizTypeAndBizId(bizType, bizId).orElse(null);
        if (existing != null) {
            if (Objects.equals(existing.getStatus(), STATUS_CANCELED)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "该收付款流水已作废，不能重复登记");
            }
            return existing;
        }

        Long actualAccountId = accountId;
        if (actualAccountId == null) {
            actualAccountId = accountService.requireDefaultAccount().getId();
        }

        FinAccount account = accountRepository.findByIdForUpdate(actualAccountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "资金账户不存在"));
        if (account.getDeleted() != null && account.getDeleted() != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "资金账户已删除");
        }
        if (!Objects.equals(account.getStatus(), FinAccountService.STATUS_ENABLED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "资金账户未启用");
        }

        BigDecimal bal = safeMoney(account.getBalance());
        BigDecimal newBal = type == TYPE_RECEIPT ? bal.add(amt) : bal.subtract(amt);
        if (type == TYPE_PAYMENT && newBal.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "资金账户余额不足，当前余额：" + bal);
        }
        account.setBalance(newBal);
        accountRepository.save(account);

        LocalDateTime now = LocalDateTime.now();
        FinPayment p = new FinPayment();
        p.setPayNo(trimToNull(payNo));
        p.setType(type);
        p.setPartnerId(partnerId);
        p.setAccountId(actualAccountId);
        p.setAmount(amt);
        p.setBizType(bizType);
        p.setBizId(bizId);
        p.setBizNo(trimToNull(bizNo));
        p.setPayDate(payDate == null ? LocalDate.now() : payDate);
        p.setMethod(trimToNull(method));
        p.setRemark(trimToNull(remark));
        p.setStatus(STATUS_COMPLETED);
        p.setCreateBy(trimToNull(operator));
        p.setCreateTime(now);
        return paymentRepository.saveAndFlush(p);
    }

    @Transactional
    public void cancelByBiz(int bizType, Long bizId, String operator) {
        if (bizId == null) return;
        FinPayment p = paymentRepository.findFirstByBizTypeAndBizId(bizType, bizId).orElse(null);
        if (p == null) return;
        if (Objects.equals(p.getStatus(), STATUS_CANCELED)) return;
        if (!Objects.equals(p.getStatus(), STATUS_COMPLETED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "收付款流水状态不允许作废");
        }

        FinAccount account = accountRepository.findByIdForUpdate(p.getAccountId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "资金账户不存在，无法作废该流水"));

        BigDecimal amt = safeMoney(p.getAmount());
        BigDecimal bal = safeMoney(account.getBalance());
        BigDecimal newBal = p.getType() == TYPE_RECEIPT ? bal.subtract(amt) : bal.add(amt);
        if (newBal.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "资金账户余额异常，无法作废该流水");
        }
        account.setBalance(newBal);
        accountRepository.save(account);

        p.setStatus(STATUS_CANCELED);
        p.setCancelBy(trimToNull(operator));
        p.setCancelTime(LocalDateTime.now());
        paymentRepository.save(p);
    }

    private static BigDecimal safeMoney(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String s = value.trim();
        return s.isEmpty() ? null : s;
    }

    private static boolean isPartnerOptionalBizType(int bizType) {
        return bizType == BIZ_TYPE_TRANSFER_OUT
                || bizType == BIZ_TYPE_TRANSFER_IN
                || bizType == BIZ_TYPE_MANUAL_RECEIPT
                || bizType == BIZ_TYPE_MANUAL_PAYMENT;
    }
}
