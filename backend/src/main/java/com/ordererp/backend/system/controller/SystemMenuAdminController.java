package com.ordererp.backend.system.controller;

import com.ordererp.backend.system.dto.admin.MenuAdminResponse;
import com.ordererp.backend.system.service.SysAdminRoleService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/menus")
@PreAuthorize("hasAuthority('sys:role:view')")
public class SystemMenuAdminController {
    private final SysAdminRoleService roleService;

    public SystemMenuAdminController(SysAdminRoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    public List<MenuAdminResponse> listMenus() {
        return roleService.listMenus();
    }
}

