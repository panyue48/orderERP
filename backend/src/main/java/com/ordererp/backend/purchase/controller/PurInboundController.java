package com.ordererp.backend.purchase.controller;

import com.ordererp.backend.common.dto.PageResponse;
import com.ordererp.backend.purchase.dto.PurInboundDetailResponse;
import com.ordererp.backend.purchase.dto.PurInboundResponse;
import com.ordererp.backend.purchase.service.PurInboundService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/purchase/inbounds")
public class PurInboundController {
    private final PurInboundService inboundService;

    public PurInboundController(PurInboundService inboundService) {
        this.inboundService = inboundService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('pur:inbound:view')")
    public PageResponse<PurInboundResponse> page(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        Pageable pageable = PageRequest.of(page, size);
        Page<PurInboundResponse> res = inboundService.page(keyword, pageable);
        return PageResponse.from(res);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('pur:inbound:view')")
    public PurInboundDetailResponse detail(@PathVariable Long id) {
        return inboundService.detail(id);
    }
}

