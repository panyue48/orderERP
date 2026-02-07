-- Stage 5 (Sales) P2 engineering: sales price view/edit perms.

-- Price view/edit perms (under sales root id=9400)
insert into sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible)
values
    (9491, 9400, '销售单价可见', null, null, 'sal:price:view', null, 'F', 90, 0),
    (9492, 9400, '销售单价可编辑', null, null, 'sal:price:edit', null, 'F', 91, 0)
as new
on duplicate key update
    parent_id = new.parent_id,
    menu_name = new.menu_name,
    perms = new.perms,
    menu_type = new.menu_type,
    sort = new.sort,
    visible = new.visible;

-- Grants: admin gets all
insert into sys_role_menu (role_id, menu_id)
values
    (1, 9491), (1, 9492)
on duplicate key update role_id = role_id;

