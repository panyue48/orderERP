-- Sales (Stage 5): allow deleting meaningless draft sales orders created by failed "quick outbound".
-- Safe for already-initialized databases:
-- - seed sys_menu / sys_role_menu idempotently

-- Button-level perm under "销售订单（记录）"
insert into sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible)
values
    (9415, 9401, '销售订单删除草稿', null, null, 'sal:order:delete', null, 'F', 5, 0)
as new
on duplicate key update
    parent_id = new.parent_id,
    menu_name = new.menu_name,
    perms = new.perms,
    menu_type = new.menu_type,
    sort = new.sort,
    visible = new.visible;

-- Grants (admin only)
insert into sys_role_menu (role_id, menu_id)
values
    (1, 9415)
on duplicate key update role_id = role_id;

