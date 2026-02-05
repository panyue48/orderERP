-- Stage 4 (Purchase) P1 extension: AP bill regenerate permission.

insert into sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible)
values
    (9346, 9304, '对账单重新生成', null, null, 'pur:ap:regen', null, 'F', 6, 0)
as new
on duplicate key update
    parent_id = new.parent_id,
    menu_name = new.menu_name,
    perms = new.perms,
    menu_type = new.menu_type,
    sort = new.sort,
    visible = new.visible;

insert into sys_role_menu (role_id, menu_id)
values
    (1, 9346)
on duplicate key update role_id = role_id;

