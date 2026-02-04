-- WMS (Stage 3) closure: inventory check bill menu + permissions.

-- Menu: check bills
insert into sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible)
values
    (9205, 9200, '盘点单', '/wms/check-bills', 'views/WmsCheckBills.vue', 'wms:check:view', 'DocumentChecked', 'C', 5, 1)
as new
on duplicate key update
    parent_id = new.parent_id,
    menu_name = new.menu_name,
    path = new.path,
    component = new.component,
    perms = new.perms,
    icon = new.icon,
    menu_type = new.menu_type,
    sort = new.sort,
    visible = new.visible;

-- Button-level perms (admin only)
insert into sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible)
values
    (9261, 9205, '盘点单新增', null, null, 'wms:check:add', null, 'F', 1, 0),
    (9262, 9205, '盘点单执行', null, null, 'wms:check:execute', null, 'F', 2, 0)
as new
on duplicate key update
    parent_id = new.parent_id,
    menu_name = new.menu_name,
    perms = new.perms,
    menu_type = new.menu_type,
    sort = new.sort,
    visible = new.visible;

-- Grants:
-- - admin (role_id=1): view + create/execute
-- - manager/sale_mgr (role_id=2): view only
insert into sys_role_menu (role_id, menu_id)
values
    (1, 9205), (1, 9261), (1, 9262),
    (2, 9205)
on duplicate key update role_id = role_id;

