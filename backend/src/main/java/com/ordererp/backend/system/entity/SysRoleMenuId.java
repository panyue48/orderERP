package com.ordererp.backend.system.entity;

import java.io.Serializable;
import java.util.Objects;

public class SysRoleMenuId implements Serializable {
    private Long roleId;
    private Long menuId;

    public SysRoleMenuId() {
    }

    public SysRoleMenuId(Long roleId, Long menuId) {
        this.roleId = roleId;
        this.menuId = menuId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SysRoleMenuId that = (SysRoleMenuId) o;
        return Objects.equals(roleId, that.roleId) && Objects.equals(menuId, that.menuId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roleId, menuId);
    }
}
