package com.ordererp.backend.system.service;

import com.ordererp.backend.system.dto.admin.MenuAdminResponse;
import com.ordererp.backend.system.dto.admin.RoleAdminCreateRequest;
import com.ordererp.backend.system.dto.admin.RoleAdminResponse;
import com.ordererp.backend.system.dto.admin.RoleAdminUpdateRequest;
import com.ordererp.backend.system.dto.admin.RoleMenuUpdateRequest;
import com.ordererp.backend.system.entity.SysMenu;
import com.ordererp.backend.system.entity.SysRole;
import com.ordererp.backend.system.entity.SysRoleMenu;
import com.ordererp.backend.system.repository.SysMenuRepository;
import com.ordererp.backend.system.repository.SysRoleMenuRepository;
import com.ordererp.backend.system.repository.SysRoleRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SysAdminRoleService {
    private final SysRoleRepository roleRepository;
    private final SysRoleMenuRepository roleMenuRepository;
    private final SysMenuRepository menuRepository;

    public SysAdminRoleService(SysRoleRepository roleRepository, SysRoleMenuRepository roleMenuRepository,
            SysMenuRepository menuRepository) {
        this.roleRepository = roleRepository;
        this.roleMenuRepository = roleMenuRepository;
        this.menuRepository = menuRepository;
    }

    public Page<RoleAdminResponse> page(String keyword, Pageable pageable) {
        return roleRepository.search(keyword, pageable).map(SysAdminRoleService::toResponse);
    }

    public RoleAdminResponse get(Long id) {
        SysRole role = roleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "角色不存在"));
        if (role.getDeleted() != null && role.getDeleted() == 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "角色不存在");
        }
        return toResponse(role);
    }

    @Transactional
    public RoleAdminResponse create(RoleAdminCreateRequest request) {
        if (roleRepository.existsByRoleKeyAndDeleted(request.roleKey(), 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "角色标识已存在");
        }

        SysRole role = new SysRole();
        role.setRoleName(request.roleName().trim());
        role.setRoleKey(request.roleKey().trim());
        role.setSort(request.sort() == null ? 0 : request.sort());
        role.setStatus(request.status() == null ? 1 : request.status());
        role.setDeleted(0);
        role.setCreateTime(LocalDateTime.now());
        SysRole saved = roleRepository.save(role);
        return toResponse(saved);
    }

    @Transactional
    public RoleAdminResponse update(Long id, RoleAdminUpdateRequest request) {
        SysRole role = roleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "角色不存在"));
        if (role.getDeleted() != null && role.getDeleted() == 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "角色不存在");
        }

        if (request.roleName() != null) {
            role.setRoleName(request.roleName().trim());
        }
        if (request.roleKey() != null) {
            String newKey = request.roleKey().trim();
            if (!newKey.equals(role.getRoleKey()) && roleRepository.existsByRoleKeyAndDeleted(newKey, 0)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "角色标识已存在");
            }
            role.setRoleKey(newKey);
        }
        if (request.sort() != null) {
            role.setSort(request.sort());
        }
        if (request.status() != null) {
            role.setStatus(request.status());
        }

        SysRole saved = roleRepository.save(role);
        return toResponse(saved);
    }

    @Transactional
    public void delete(Long id) {
        SysRole role = roleRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "角色不存在"));
        role.setDeleted(1);
        roleRepository.save(role);
        roleMenuRepository.deleteByRoleId(id);
    }

    public List<Long> getRoleMenuIds(Long roleId) {
        // Validate role exists
        SysRole role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "角色不存在"));
        if (role.getDeleted() != null && role.getDeleted() == 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "角色不存在");
        }
        return roleMenuRepository.findByRoleId(roleId).stream().map(SysRoleMenu::getMenuId).distinct().sorted().toList();
    }

    @Transactional
    public void updateRoleMenus(Long roleId, RoleMenuUpdateRequest request) {
        // Validate role exists
        SysRole role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "角色不存在"));
        if (role.getDeleted() != null && role.getDeleted() == 1) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "角色不存在");
        }

        List<Long> menuIds = normalizeIds(request.menuIds());
        roleMenuRepository.deleteByRoleId(roleId);
        if (menuIds.isEmpty()) {
            return;
        }

        Set<Long> existing = menuRepository.findAllById(menuIds).stream().map(SysMenu::getId).collect(Collectors.toSet());
        List<Long> missing = menuIds.stream().filter(id -> !existing.contains(id)).toList();
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "菜单不存在: " + missing);
        }

        List<SysRoleMenu> links = new ArrayList<>();
        for (Long menuId : menuIds) {
            SysRoleMenu link = new SysRoleMenu();
            link.setRoleId(roleId);
            link.setMenuId(menuId);
            links.add(link);
        }
        roleMenuRepository.saveAll(links);
    }

    public List<MenuAdminResponse> listMenus() {
        return menuRepository.findAll().stream()
                .sorted(Comparator.comparing(SysMenu::getSort, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(SysMenu::getId))
                .map(SysAdminRoleService::toMenuResponse)
                .toList();
    }

    private static RoleAdminResponse toResponse(SysRole role) {
        return new RoleAdminResponse(role.getId(), role.getRoleName(), role.getRoleKey(), role.getSort(),
                role.getStatus());
    }

    private static MenuAdminResponse toMenuResponse(SysMenu m) {
        return new MenuAdminResponse(
                m.getId(),
                m.getParentId(),
                m.getMenuName(),
                m.getPath(),
                m.getComponent(),
                m.getPerms(),
                m.getIcon(),
                m.getMenuType(),
                m.getSort(),
                m.getVisible());
    }

    private static List<Long> normalizeIds(List<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream()
                .filter(id -> id != null && id > 0)
                .distinct()
                .sorted()
                .toList();
    }
}

