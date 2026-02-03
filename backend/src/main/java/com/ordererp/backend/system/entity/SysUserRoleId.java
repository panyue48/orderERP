package com.ordererp.backend.system.entity;

import java.io.Serializable;
import java.util.Objects;

public class SysUserRoleId implements Serializable {
    private Long userId;
    private Long roleId;

    public SysUserRoleId() {
    }

    public SysUserRoleId(Long userId, Long roleId) {
        this.userId = userId;
        this.roleId = roleId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SysUserRoleId that = (SysUserRoleId) o;
        return Objects.equals(userId, that.userId) && Objects.equals(roleId, that.roleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, roleId);
    }
}
