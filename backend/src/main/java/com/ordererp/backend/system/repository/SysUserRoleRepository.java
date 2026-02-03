package com.ordererp.backend.system.repository;

import com.ordererp.backend.system.entity.SysUserRole;
import com.ordererp.backend.system.entity.SysUserRoleId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SysUserRoleRepository extends JpaRepository<SysUserRole, SysUserRoleId> {
    List<SysUserRole> findByUserId(Long userId);

    void deleteByUserId(Long userId);
}

