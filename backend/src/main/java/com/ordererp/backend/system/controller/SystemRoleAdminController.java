package com.ordererp.backend.system.controller;

import com.ordererp.backend.system.dto.admin.MenuAdminResponse;
import com.ordererp.backend.system.dto.admin.RoleAdminCreateRequest;
import com.ordererp.backend.system.dto.admin.RoleAdminResponse;
import com.ordererp.backend.system.dto.admin.RoleAdminUpdateRequest;
import com.ordererp.backend.system.dto.admin.RoleMenuUpdateRequest;
import com.ordererp.backend.system.service.SysAdminRoleService;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/system/roles")
@PreAuthorize("hasAuthority('sys:role:view')")
public class SystemRoleAdminController {
    private final SysAdminRoleService roleService;

    public SystemRoleAdminController(SysAdminRoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    public PageResponse<RoleAdminResponse> page(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        Pageable pageable = PageRequest.of(page, size);
        Page<RoleAdminResponse> result = roleService.page(keyword, pageable);
        return PageResponse.from(result);
    }

    @GetMapping("/{id}")
    public RoleAdminResponse get(@PathVariable Long id) {
        return roleService.get(id);
    }

    @PostMapping
    public RoleAdminResponse create(@Valid @RequestBody RoleAdminCreateRequest request) {
        return roleService.create(request);
    }

    @PutMapping("/{id}")
    public RoleAdminResponse update(@PathVariable Long id, @Valid @RequestBody RoleAdminUpdateRequest request) {
        return roleService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        roleService.delete(id);
    }

    @GetMapping("/{id}/menus")
    public List<Long> getRoleMenus(@PathVariable Long id) {
        return roleService.getRoleMenuIds(id);
    }

    @PutMapping("/{id}/menus")
    public void updateRoleMenus(@PathVariable Long id, @Valid @RequestBody RoleMenuUpdateRequest request) {
        roleService.updateRoleMenus(id, request);
    }

    @GetMapping("/menus")
    public List<MenuAdminResponse> listMenus() {
        return roleService.listMenus();
    }
}
