-- WMS (Stage 3) enhancements: export + reversal permissions.

-- Stock export
insert into sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible)
values
    (9251, 9201, '库存导出', null, null, 'wms:stock:export', null, 'F', 10, 0)
as new
on duplicate key update parent_id = new.parent_id, menu_name = new.menu_name, perms = new.perms, menu_type = new.menu_type, sort = new.sort, visible = new.visible;

-- Stock log export
insert into sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible)
values
    (9252, 9204, '流水导出', null, null, 'wms:stocklog:export', null, 'F', 10, 0)
as new
on duplicate key update parent_id = new.parent_id, menu_name = new.menu_name, perms = new.perms, menu_type = new.menu_type, sort = new.sort, visible = new.visible;

-- Reversal (admin only)
insert into sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible)
values
    (9253, 9202, '入库冲销', null, null, 'wms:stockin:reverse', null, 'F', 20, 0),
    (9254, 9203, '出库冲销', null, null, 'wms:stockout:reverse', null, 'F', 20, 0)
as new
on duplicate key update parent_id = new.parent_id, menu_name = new.menu_name, perms = new.perms, menu_type = new.menu_type, sort = new.sort, visible = new.visible;

-- Grants:
-- - admin (role_id=1): export + reverse
-- - manager/sale_mgr (role_id=2): export only
insert into sys_role_menu (role_id, menu_id)
values
    (1, 9251), (1, 9252), (1, 9253), (1, 9254),
    (2, 9251), (2, 9252)
on duplicate key update role_id = role_id;

