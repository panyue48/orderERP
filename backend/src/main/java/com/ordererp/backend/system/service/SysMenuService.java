package com.ordererp.backend.system.service;

import com.ordererp.backend.system.dto.MenuMetaDto;
import com.ordererp.backend.system.dto.MenuRouterDto;
import com.ordererp.backend.system.entity.SysMenu;
import com.ordererp.backend.system.repository.SysMenuRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class SysMenuService {
    private final SysMenuRepository menuRepository;

    public SysMenuService(SysMenuRepository menuRepository) {
        this.menuRepository = menuRepository;
    }

    public List<MenuRouterDto> getRoutersByUserId(Long userId) {
        List<SysMenu> menus = menuRepository.findMenusByUserId(userId);
        Map<Long, List<SysMenu>> grouped = menus.stream()
                .collect(Collectors.groupingBy(menu -> menu.getParentId() == null ? 0L : menu.getParentId()));
        return buildTree(0L, grouped);
    }

    private List<MenuRouterDto> buildTree(Long parentId, Map<Long, List<SysMenu>> grouped) {
        List<SysMenu> children = grouped.getOrDefault(parentId, List.of()).stream()
                .filter(menu -> !"F".equalsIgnoreCase(menu.getMenuType()))
                .sorted(Comparator.comparing(SysMenu::getSort, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(SysMenu::getId))
                .toList();

        List<MenuRouterDto> results = new ArrayList<>();
        for (SysMenu menu : children) {
            MenuRouterDto dto = new MenuRouterDto();
            dto.setName(menu.getMenuName());
            dto.setPath(menu.getPath());
            dto.setComponent(resolveComponent(menu));
            dto.setMeta(new MenuMetaDto(menu.getMenuName(), menu.getIcon()));
            List<MenuRouterDto> next = buildTree(menu.getId(), grouped);
            if (!next.isEmpty()) {
                dto.setChildren(next);
            }
            results.add(dto);
        }
        return results;
    }

    private String resolveComponent(SysMenu menu) {
        if ("M".equalsIgnoreCase(menu.getMenuType())) {
            if (menu.getComponent() == null || menu.getComponent().isBlank()) {
                return "RouteView";
            }
        }
        return Objects.requireNonNullElse(menu.getComponent(), "views/Placeholder.vue");
    }
}
