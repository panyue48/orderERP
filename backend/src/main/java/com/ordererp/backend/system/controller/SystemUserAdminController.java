package com.ordererp.backend.system.controller;

import com.ordererp.backend.system.dto.admin.UserAdminCreateRequest;
import com.ordererp.backend.system.dto.admin.UserAdminResponse;
import com.ordererp.backend.system.dto.admin.UserAdminUpdateRequest;
import com.ordererp.backend.system.service.SysAdminUserService;
import jakarta.validation.Valid;
import com.ordererp.backend.common.dto.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/users")
@PreAuthorize("hasAuthority('sys:user:view')")
public class SystemUserAdminController {
    private final SysAdminUserService userService;

    public SystemUserAdminController(SysAdminUserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public PageResponse<UserAdminResponse> page(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        Pageable pageable = PageRequest.of(page, size);
        Page<UserAdminResponse> result = userService.page(keyword, pageable);
        return PageResponse.from(result);
    }

    @GetMapping("/{id}")
    public UserAdminResponse get(@PathVariable Long id) {
        return userService.get(id);
    }

    @PostMapping
    public UserAdminResponse create(@Valid @RequestBody UserAdminCreateRequest request) {
        return userService.create(request);
    }

    @PutMapping("/{id}")
    public UserAdminResponse update(@PathVariable Long id, @Valid @RequestBody UserAdminUpdateRequest request) {
        return userService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        userService.delete(id);
    }
}
