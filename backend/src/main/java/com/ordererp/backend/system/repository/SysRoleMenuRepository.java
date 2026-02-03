package com.ordererp.backend.system.repository;

import com.ordererp.backend.system.entity.SysRoleMenu;
import com.ordererp.backend.system.entity.SysRoleMenuId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SysRoleMenuRepository extends JpaRepository<SysRoleMenu, SysRoleMenuId> {
    List<SysRoleMenu> findByRoleId(Long roleId);

    void deleteByRoleId(Long roleId);
}

