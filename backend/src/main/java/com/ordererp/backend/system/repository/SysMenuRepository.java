package com.ordererp.backend.system.repository;

import com.ordererp.backend.system.entity.SysMenu;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SysMenuRepository extends JpaRepository<SysMenu, Long> {
    @Query("""
            select distinct m from SysMenu m
            join SysRoleMenu rm on m.id = rm.menuId
            join SysUserRole ur on rm.roleId = ur.roleId
            where ur.userId = :userId and m.visible = 1
            order by m.sort asc, m.id asc
            """)
    List<SysMenu> findMenusByUserId(@Param("userId") Long userId);

    @Query("""
            select distinct m.perms from SysMenu m
            join SysRoleMenu rm on m.id = rm.menuId
            join SysUserRole ur on rm.roleId = ur.roleId
            where ur.userId = :userId and m.perms is not null and m.perms <> ''
            """)
    List<String> findPermsByUserId(@Param("userId") Long userId);
}
