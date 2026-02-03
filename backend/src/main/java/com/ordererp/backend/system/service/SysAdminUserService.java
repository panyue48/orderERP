package com.ordererp.backend.system.service;

import com.ordererp.backend.system.dto.admin.UserAdminCreateRequest;
import com.ordererp.backend.system.dto.admin.UserAdminResponse;
import com.ordererp.backend.system.dto.admin.UserAdminUpdateRequest;
import com.ordererp.backend.system.entity.SysRole;
import com.ordererp.backend.system.entity.SysUser;
import com.ordererp.backend.system.entity.SysUserRole;
import com.ordererp.backend.system.repository.SysRoleRepository;
import com.ordererp.backend.system.repository.SysUserRepository;
import com.ordererp.backend.system.repository.SysUserRoleRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SysAdminUserService {
    private final SysUserRepository userRepository;
    private final SysRoleRepository roleRepository;
    private final SysUserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    public SysAdminUserService(SysUserRepository userRepository, SysRoleRepository roleRepository,
            SysUserRoleRepository userRoleRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Page<UserAdminResponse> page(String keyword, Pageable pageable) {
        Page<SysUser> page = userRepository.search(keyword, pageable);
        List<Long> userIds = page.getContent().stream().map(SysUser::getId).toList();
        Map<Long, List<Long>> userToRoleIds = loadUserRoleIds(userIds);
        Map<Long, String> roleIdToKey = loadRoleKeys(userToRoleIds.values().stream().flatMap(List::stream).toList());
        return page.map(user -> toResponse(user, userToRoleIds.getOrDefault(user.getId(), List.of()), roleIdToKey));
    }

    public UserAdminResponse get(Long id) {
        SysUser user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        if (user.getDeleted() != null && user.getDeleted() == 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        List<Long> roleIds = userRoleRepository.findByUserId(id).stream().map(SysUserRole::getRoleId).toList();
        Map<Long, String> roleIdToKey = loadRoleKeys(roleIds);
        return toResponse(user, roleIds, roleIdToKey);
    }

    @Transactional
    public UserAdminResponse create(UserAdminCreateRequest request) {
        if (userRepository.existsByUsernameAndDeleted(request.username(), 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名已存在");
        }

        SysUser user = new SysUser();
        user.setUsername(request.username().trim());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setNickname(trimToNull(request.nickname()));
        user.setEmail(trimToNull(request.email()));
        user.setPhone(trimToNull(request.phone()));
        user.setAvatar(trimToNull(request.avatar()));
        user.setStatus(request.status() == null ? 1 : request.status());
        user.setDeleted(0);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());

        SysUser saved = userRepository.save(user);
        List<Long> roleIds = normalizeIds(request.roleIds());
        updateUserRoles(saved.getId(), roleIds);

        Map<Long, String> roleIdToKey = loadRoleKeys(roleIds);
        return toResponse(saved, roleIds, roleIdToKey);
    }

    @Transactional
    public UserAdminResponse update(Long id, UserAdminUpdateRequest request) {
        SysUser user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        if (user.getDeleted() != null && user.getDeleted() == 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }

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
        if (request.status() != null) {
            user.setStatus(request.status());
        }
        user.setUpdateTime(LocalDateTime.now());

        SysUser saved = userRepository.save(user);
        List<Long> roleIds = normalizeIds(request.roleIds());
        if (request.roleIds() != null) {
            updateUserRoles(id, roleIds);
        }

        Map<Long, String> roleIdToKey = loadRoleKeys(roleIds);
        return toResponse(saved, roleIds, roleIdToKey);
    }

    @Transactional
    public void delete(Long id) {
        SysUser user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在"));
        user.setDeleted(1);
        user.setUpdateTime(LocalDateTime.now());
        userRepository.save(user);
        userRoleRepository.deleteByUserId(id);
    }

    private void updateUserRoles(Long userId, List<Long> roleIds) {
        userRoleRepository.deleteByUserId(userId);
        if (roleIds.isEmpty()) {
            return;
        }

        // Validate role existence (avoid silent bad ids).
        Set<Long> existing = roleRepository.findAllById(roleIds).stream().map(SysRole::getId).collect(Collectors.toSet());
        List<Long> missing = roleIds.stream().filter(id -> !existing.contains(id)).toList();
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "角色不存在: " + missing);
        }

        List<SysUserRole> links = new ArrayList<>();
        for (Long roleId : roleIds) {
            SysUserRole link = new SysUserRole();
            link.setUserId(userId);
            link.setRoleId(roleId);
            links.add(link);
        }
        userRoleRepository.saveAll(links);
    }

    private Map<Long, List<Long>> loadUserRoleIds(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        // N+1 safe for small page sizes, but we can still batch with per-user queries.
        return userIds.stream().collect(Collectors.toMap(id -> id,
                id -> userRoleRepository.findByUserId(id).stream().map(SysUserRole::getRoleId).toList()));
    }

    private Map<Long, String> loadRoleKeys(List<Long> roleIds) {
        List<Long> ids = normalizeIds(roleIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        return roleRepository.findAllById(ids).stream()
                .filter(r -> r.getDeleted() == null || r.getDeleted() == 0)
                .collect(Collectors.toMap(SysRole::getId, SysRole::getRoleKey));
    }

    private static UserAdminResponse toResponse(SysUser user, List<Long> roleIds, Map<Long, String> roleIdToKey) {
        List<Long> ids = normalizeIds(roleIds);
        List<String> keys = ids.stream()
                .map(roleIdToKey::get)
                .filter(k -> k != null && !k.isBlank())
                .sorted()
                .toList();
        return new UserAdminResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getEmail(),
                user.getPhone(),
                user.getAvatar(),
                user.getStatus(),
                ids,
                keys);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String s = value.trim();
        return s.isEmpty() ? null : s;
    }

    private static List<Long> normalizeIds(List<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();
    }
}

