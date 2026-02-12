package com.ordererp.backend.finance.service;

import com.ordererp.backend.finance.dto.FinAccountCreateRequest;
import com.ordererp.backend.finance.dto.FinAccountOptionResponse;
import com.ordererp.backend.finance.dto.FinAccountResponse;
import com.ordererp.backend.finance.entity.FinAccount;
import com.ordererp.backend.finance.repository.FinAccountRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FinAccountService {
    public static final int STATUS_ENABLED = 1;

    private final FinAccountRepository accountRepository;

    public FinAccountService(FinAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public List<FinAccountOptionResponse> options(String keyword, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 50));
        return accountRepository.optionRows(trimToNull(keyword), safeLimit).stream()
                .map(r -> new FinAccountOptionResponse(r.getId(), r.getAccountName(), r.getBalance()))
                .toList();
    }

    @Transactional
    public FinAccountResponse create(FinAccountCreateRequest request, String operator) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        String name = trimToNull(request.accountName());
        if (name == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "accountName is required");
        BigDecimal opening = safeMoney(request.openingBalance());
        if (opening.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "openingBalance must be >= 0");
        }

        LocalDateTime now = LocalDateTime.now();
        FinAccount a = new FinAccount();
        a.setAccountName(name);
        a.setAccountNo(trimToNull(request.accountNo()));
        a.setBalance(opening);
        a.setRemark(trimToNull(request.remark()));
        a.setStatus(STATUS_ENABLED);
        a.setDeleted(0);
        a.setCreateBy(trimToNull(operator));
        a.setCreateTime(now);
        a.setUpdateBy(trimToNull(operator));
        a.setUpdateTime(now);
        a = accountRepository.saveAndFlush(a);
        return toResponse(a);
    }

    public FinAccount requireDefaultAccount() {
        return accountRepository.findFirstByDeletedAndStatusOrderByIdAsc(0, STATUS_ENABLED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先创建并启用一个资金账户"));
    }

    public static FinAccountResponse toResponse(FinAccount a) {
        return new FinAccountResponse(
                a.getId(),
                a.getAccountName(),
                a.getAccountNo(),
                a.getBalance(),
                a.getRemark(),
                a.getStatus(),
                a.getCreateBy(),
                a.getCreateTime(),
                a.getUpdateBy(),
                a.getUpdateTime());
    }

    private static BigDecimal safeMoney(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String s = value.trim();
        return s.isEmpty() ? null : s;
    }
}

