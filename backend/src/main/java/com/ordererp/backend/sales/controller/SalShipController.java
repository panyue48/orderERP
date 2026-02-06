package com.ordererp.backend.sales.controller;

import com.ordererp.backend.common.dto.PageResponse;
import com.ordererp.backend.sales.dto.SalShipDetailResponse;
import com.ordererp.backend.sales.dto.SalShipReverseResponse;
import com.ordererp.backend.sales.dto.SalShipResponse;
import com.ordererp.backend.sales.service.SalShipService;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import com.ordererp.backend.system.security.SysUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sales/ships")
public class SalShipController {
    private final SalShipService shipService;

    public SalShipController(SalShipService shipService) {
        this.shipService = shipService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('sal:ship:view')")
    public PageResponse<SalShipResponse> page(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        Pageable pageable = PageRequest.of(page, size);
        Page<SalShipResponse> res = shipService.page(keyword, customerId, warehouseId, startDate, endDate, pageable);
        return PageResponse.from(res);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sal:ship:view')")
    public SalShipDetailResponse detail(@PathVariable Long id) {
        return shipService.detail(id);
    }

    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAuthority('sal:ship:reverse')")
    public SalShipReverseResponse reverse(@PathVariable Long id, Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return shipService.reverse(id, user.getUsername());
    }
}
