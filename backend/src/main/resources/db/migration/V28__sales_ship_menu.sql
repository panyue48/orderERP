-- Sales (Stage 5): sales outbound records (shipment list) menu + permissions.
-- Safe for already-initialized databases:
-- - seed sys_menu / sys_role_menu with high ids

insert into sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible)
values
    (9402, 9400, '销售出库单', '/sales/outbounds', 'views/SalesOutbounds.vue', 'sal:ship:view', 'Van', 'C', 2, 1)
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
    (9421, 9402, '销售出库单详情', null, null, 'sal:ship:detail', null, 'F', 1, 0)
as new
on duplicate key update
    parent_id = new.parent_id,
    menu_name = new.menu_name,
    perms = new.perms,
    menu_type = new.menu_type,
    sort = new.sort,
    visible = new.visible;

-- Grants:
-- - admin (role_id=1): view + detail
-- - manager/sale_mgr (role_id=2): view only
insert into sys_role_menu (role_id, menu_id)
values
    (1, 9402), (1, 9421),
    (2, 9402)
on duplicate key update role_id = role_id;

