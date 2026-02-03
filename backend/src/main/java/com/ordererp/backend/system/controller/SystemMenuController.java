package com.ordererp.backend.system.controller;

import com.ordererp.backend.system.dto.MenuRouterDto;
import com.ordererp.backend.system.security.SysUserDetails;
import com.ordererp.backend.system.service.SysMenuService;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/menu")
public class SystemMenuController {
    private final SysMenuService menuService;

    public SystemMenuController(SysMenuService menuService) {
        this.menuService = menuService;
    }

    @GetMapping("/routers")
    public List<MenuRouterDto> routers(Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return menuService.getRoutersByUserId(user.getId());
    }
}
