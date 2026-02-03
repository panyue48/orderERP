package com.ordererp.backend.system.service;

import com.ordererp.backend.system.dto.LoginRequest;
import com.ordererp.backend.system.dto.LoginResponse;
import com.ordererp.backend.system.dto.UserInfo;
import com.ordererp.backend.system.security.JwtService;
import com.ordererp.backend.system.security.SysUserDetails;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(AuthenticationManager authenticationManager, JwtService jwtService) {
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        SysUserDetails user = (SysUserDetails) authentication.getPrincipal();
        String token = jwtService.generateToken(user);
        return new LoginResponse(token, new UserInfo(user.getId(), user.getUsername(), user.getNickname()));
    }
}
