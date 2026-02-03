package com.ordererp.backend.system.service;

import com.ordererp.backend.system.dto.ChangePasswordRequest;
import com.ordererp.backend.system.dto.UserProfileResponse;
import com.ordererp.backend.system.dto.UserProfileUpdateRequest;
import com.ordererp.backend.system.entity.SysUser;
import com.ordererp.backend.system.repository.SysUserRepository;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SysUserService {
    private final SysUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public SysUserService(SysUserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public UserProfileResponse getProfile(Long userId) {
        SysUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        return toProfile(user);
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UserProfileUpdateRequest request) {
        SysUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));

        // Allow partial updates: null means "no change".
        if (request.nickname() != null) {
            user.setNickname(trimToNull(request.nickname()));
        }
        if (request.email() != null) {
            user.setEmail(trimToNull(request.email()));
        }
        if (request.phone() != null) {
            user.setPhone(trimToNull(request.phone()));
        }
        if (request.avatar() != null) {
            user.setAvatar(trimToNull(request.avatar()));
        }

        return toProfile(userRepository.save(user));
    }

    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        SysUser user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));

        String stored = user.getPassword();
        if (stored != null && !stored.startsWith("{")) {
            stored = "{noop}" + stored;
        }

        if (!passwordEncoder.matches(request.oldPassword(), Objects.requireNonNullElse(stored, ""))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "原密码错误");
        }
        if (request.oldPassword().equals(request.newPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新密码不能与原密码相同");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    private static UserProfileResponse toProfile(SysUser user) {
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getEmail(),
                user.getPhone(),
                user.getAvatar());
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String s = value.trim();
        return s.isEmpty() ? null : s;
    }
}

