-- Base Data (Stage 2) permission hardening:
-- introduce button-level perms for CRUD actions so "view" doesn't imply "write".

-- Products
insert into sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible)
values
    (9111, 9101, '商品新增', null, null, 'base:product:add', null, 'F', 1, 0),
    (9112, 9101, '商品编辑', null, null, 'base:product:edit', null, 'F', 2, 0),
    (9113, 9101, '商品删除', null, null, 'base:product:remove', null, 'F', 3, 0)
as new
on duplicate key update
    parent_id = new.parent_id,
    menu_name = new.menu_name,
    perms = new.perms,
    menu_type = new.menu_type,
    sort = new.sort,
    visible = new.visible;

-- Warehouses
insert into sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible)
values
    (9121, 9102, '仓库新增', null, null, 'base:warehouse:add', null, 'F', 1, 0),
    (9122, 9102, '仓库编辑', null, null, 'base:warehouse:edit', null, 'F', 2, 0),
    (9123, 9102, '仓库删除', null, null, 'base:warehouse:remove', null, 'F', 3, 0)
as new
on duplicate key update
    parent_id = new.parent_id,
    menu_name = new.menu_name,
    perms = new.perms,
    menu_type = new.menu_type,
    sort = new.sort,
    visible = new.visible;

-- Partners
insert into sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible)
values
    (9131, 9103, '往来单位新增', null, null, 'base:partner:add', null, 'F', 1, 0),
    (9132, 9103, '往来单位编辑', null, null, 'base:partner:edit', null, 'F', 2, 0),
    (9133, 9103, '往来单位删除', null, null, 'base:partner:remove', null, 'F', 3, 0)
as new
on duplicate key update
    parent_id = new.parent_id,
    menu_name = new.menu_name,
    perms = new.perms,
    menu_type = new.menu_type,
    sort = new.sort,
    visible = new.visible;

-- Grant full CRUD permissions to admin role (id=1).
insert into sys_role_menu (role_id, menu_id)
values
    (1, 9111), (1, 9112), (1, 9113),
    (1, 9121), (1, 9122), (1, 9123),
    (1, 9131), (1, 9132), (1, 9133)
on duplicate key update role_id = role_id;

