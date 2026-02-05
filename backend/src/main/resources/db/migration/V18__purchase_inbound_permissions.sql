-- Purchase (Stage 4+) menu/permission hardening: add button-level perms for inbound operations.
-- Safe for already-initialized databases (idempotent inserts).

-- Button-level perms under "采购入库单" (menu_id=9302)
insert into sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible)
values
    (9322, 9302, '采购入库单新增', null, null, 'pur:inbound:add', null, 'F', 2, 0)
as new
on duplicate key update
    parent_id = new.parent_id,
    menu_name = new.menu_name,
    perms = new.perms,
    menu_type = new.menu_type,
    sort = new.sort,
    visible = new.visible;

-- Grants:
-- - admin (role_id=1): add
insert into sys_role_menu (role_id, menu_id)
values
    (1, 9322)
on duplicate key update role_id = role_id;

