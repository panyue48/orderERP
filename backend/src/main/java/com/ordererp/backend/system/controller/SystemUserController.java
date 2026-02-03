package com.ordererp.backend.system.controller;

import com.ordererp.backend.system.dto.ChangePasswordRequest;
import com.ordererp.backend.system.dto.UserProfileResponse;
import com.ordererp.backend.system.dto.UserProfileUpdateRequest;
import com.ordererp.backend.system.security.SysUserDetails;
import com.ordererp.backend.system.service.SysUserService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/user")
public class SystemUserController {
    private final SysUserService userService;

    public SystemUserController(SysUserService userService) {
        this.userService = userService;
    }

    @GetMapping("/profile")
    public UserProfileResponse profile(Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return userService.getProfile(user.getId());
    }

    @PutMapping("/profile")
    public UserProfileResponse updateProfile(@Valid @RequestBody UserProfileUpdateRequest request,
            Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        return userService.updateProfile(user.getId(), request);
    }

    @PostMapping("/profile/password")
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request, Authentication authentication) {
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        userService.changePassword(user.getId(), request);
    }

    @GetMapping("/perms")
    public List<String> perms(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(p -> p != null && !p.isBlank())
                .distinct()
                .sorted()
                .toList();
    }
}
