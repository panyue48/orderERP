package com.ordererp.backend.finance.controller;

import com.ordererp.backend.common.dto.PageResponse;
import com.ordererp.backend.finance.dto.FinAccountCreateRequest;
import com.ordererp.backend.finance.dto.FinAccountOptionResponse;
import com.ordererp.backend.finance.dto.FinAccountResponse;
import com.ordererp.backend.finance.repository.FinAccountRepository;
import com.ordererp.backend.finance.service.FinAccountService;
import com.ordererp.backend.system.security.SysUserDetails;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/finance/accounts")
public class FinAccountController {
    private final FinAccountService accountService;
    private final FinAccountRepository accountRepository;

    public FinAccountController(FinAccountService accountService, FinAccountRepository accountRepository) {
        this.accountService = accountService;
        this.accountRepository = accountRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('fin:account:view')")
    public PageResponse<FinAccountResponse> page(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 200)));
        Page<FinAccountResponse> res = accountRepository.pageRows(keyword == null ? null : keyword.trim(), pageable)
                .map(r -> new FinAccountResponse(
                        r.getId(),
                        r.getAccountName(),
                        r.getAccountNo(),
                        r.getBalance(),
                        r.getRemark(),
                        r.getStatus(),
                        r.getCreateBy(),
                        r.getCreateTime(),
                        r.getUpdateBy(),
                        r.getUpdateTime()));
        return PageResponse.from(res);
    }

    @GetMapping("/options")
    @PreAuthorize("hasAuthority('fin:account:view')")
    public List<FinAccountOptionResponse> options(@RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "20") int limit) {
        return accountService.options(keyword, limit);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('fin:account:add')")
    public FinAccountResponse create(@Valid @RequestBody FinAccountCreateRequest request, Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return accountService.create(request, user.getUsername());
    }
}
