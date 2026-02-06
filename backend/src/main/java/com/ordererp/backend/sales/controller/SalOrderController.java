package com.ordererp.backend.sales.controller;

import com.ordererp.backend.common.dto.PageResponse;
import com.ordererp.backend.sales.dto.SalOrderCreateRequest;
import com.ordererp.backend.sales.dto.SalOrderDetailResponse;
import com.ordererp.backend.sales.dto.SalOrderResponse;
import com.ordererp.backend.sales.dto.SalOrderOptionResponse;
import com.ordererp.backend.sales.dto.SalShipBatchRequest;
import com.ordererp.backend.sales.dto.SalShipDetailResponse;
import com.ordererp.backend.sales.dto.SalShipResponse;
import com.ordererp.backend.sales.service.SalOrderService;
import com.ordererp.backend.system.security.SysUserDetails;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/sales/orders")
public class SalOrderController {
    private final SalOrderService orderService;

    public SalOrderController(SalOrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('sal:order:view')")
    public PageResponse<SalOrderResponse> page(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate) {
        Pageable pageable = PageRequest.of(page, size);
        Page<SalOrderResponse> res = orderService.page(keyword, customerId, startDate, endDate, pageable);
        return PageResponse.from(res);
    }

    @GetMapping("/options")
    @PreAuthorize("hasAuthority('sal:order:view')")
    public List<SalOrderOptionResponse> options(@RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "200") int limit) {
        return orderService.options(keyword, limit);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sal:order:view')")
    public SalOrderDetailResponse detail(@PathVariable Long id) {
        return orderService.detail(id);
    }

    @GetMapping("/{id}/ships")
    @PreAuthorize("hasAuthority('sal:order:view')")
    public List<SalShipResponse> ships(@PathVariable Long id) {
        return orderService.listShips(id);
    }

    @GetMapping("/ships/{shipId}")
    @PreAuthorize("hasAuthority('sal:order:view')")
    public SalShipDetailResponse shipDetail(@PathVariable Long shipId) {
        return orderService.shipDetail(shipId);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('sal:order:add')")
    public SalOrderResponse create(@Valid @RequestBody SalOrderCreateRequest request, Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return orderService.create(request, user.getUsername());
    }

    @PostMapping("/{id}/audit")
    @PreAuthorize("hasAuthority('sal:order:audit')")
    public SalOrderResponse audit(@PathVariable Long id, Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return orderService.audit(id, user.getUsername());
    }

    @PostMapping("/{id}/ship")
    @PreAuthorize("hasAuthority('sal:order:ship')")
    public SalOrderResponse ship(@PathVariable Long id, Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return orderService.ship(id, user.getUsername());
    }

    @PostMapping("/{id}/ships")
    @PreAuthorize("hasAuthority('sal:order:ship-batch')")
    public SalOrderResponse shipBatch(@PathVariable Long id, @Valid @RequestBody SalShipBatchRequest request, Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        List<SalOrderService.ShipLine> lines = request.lines() == null ? List.of()
                : request.lines().stream().map(l -> new SalOrderService.ShipLine(l.orderDetailId(), l.productId(), l.qty())).toList();
        orderService.shipBatch(id, lines, user.getUsername());
        return orderService.get(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('sal:order:cancel')")
    public SalOrderResponse cancel(@PathVariable Long id, Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return orderService.cancel(id, user.getUsername());
    }
}
