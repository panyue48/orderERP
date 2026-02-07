package com.ordererp.backend.sales.controller;

import com.ordererp.backend.sales.dto.SalCreditUsageResponse;
import com.ordererp.backend.sales.service.SalCreditService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sales/credit")
public class SalCreditController {
    private final SalCreditService creditService;

    public SalCreditController(SalCreditService creditService) {
        this.creditService = creditService;
    }

    @GetMapping("/customers/{customerId}")
    @PreAuthorize("hasAuthority('sal:order:view') or hasAuthority('sal:order:add') or hasAuthority('base:partner:view')")
    public SalCreditUsageResponse customerUsage(@PathVariable Long customerId) {
        return creditService.getUsage(customerId);
    }

    @GetMapping("/customers")
    @PreAuthorize("hasAuthority('sal:order:view') or hasAuthority('sal:order:add') or hasAuthority('base:partner:view')")
    public List<SalCreditUsageResponse> customerUsageBatch(@RequestParam List<Long> customerIds) {
        return creditService.listUsage(customerIds);
    }
}

