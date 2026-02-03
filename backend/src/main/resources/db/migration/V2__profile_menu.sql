-- Add "个人信息" page under 系统管理 for stage-1 user profile editing.

insert into sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible)
values
    (9006, 9003, '个人信息', '/system/profile', 'views/Profile.vue', 'sys:user:profile', 'User', 'C', 0, 1)
on duplicate key update
    parent_id = values(parent_id),
    menu_name = values(menu_name),
    path = values(path),
    component = values(component),
    perms = values(perms),
    icon = values(icon),
    menu_type = values(menu_type),
    sort = values(sort),
    visible = values(visible);

insert into sys_role_menu (role_id, menu_id)
values
    (1, 9006),
    (2, 9006)
on duplicate key update role_id = role_id;

