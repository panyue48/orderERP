-- Excel import/export permissions for Base Data.

-- Products
insert into sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible)
values
    (9151, 9101, '商品导出', null, null, 'base:product:export', null, 'F', 10, 0),
    (9152, 9101, '商品导入', null, null, 'base:product:import', null, 'F', 11, 0)
as new
on duplicate key update parent_id = new.parent_id, menu_name = new.menu_name, perms = new.perms, menu_type = new.menu_type, sort = new.sort, visible = new.visible;

-- Warehouses
insert into sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible)
values
    (9161, 9102, '仓库导出', null, null, 'base:warehouse:export', null, 'F', 10, 0),
    (9162, 9102, '仓库导入', null, null, 'base:warehouse:import', null, 'F', 11, 0)
as new
on duplicate key update parent_id = new.parent_id, menu_name = new.menu_name, perms = new.perms, menu_type = new.menu_type, sort = new.sort, visible = new.visible;

-- Partners
insert into sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible)
values
    (9171, 9103, '往来单位导出', null, null, 'base:partner:export', null, 'F', 10, 0),
    (9172, 9103, '往来单位导入', null, null, 'base:partner:import', null, 'F', 11, 0)
as new
on duplicate key update parent_id = new.parent_id, menu_name = new.menu_name, perms = new.perms, menu_type = new.menu_type, sort = new.sort, visible = new.visible;

-- Categories
insert into sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible)
values
    (9181, 9104, '分类导出', null, null, 'base:category:export', null, 'F', 10, 0),
    (9182, 9104, '分类导入', null, null, 'base:category:import', null, 'F', 11, 0)
as new
on duplicate key update parent_id = new.parent_id, menu_name = new.menu_name, perms = new.perms, menu_type = new.menu_type, sort = new.sort, visible = new.visible;

-- Grants:
-- - admin (role_id=1): export + import
-- - manager/sale_mgr (role_id=2): export only (read-only)
insert into sys_role_menu (role_id, menu_id)
values
    (1, 9151), (1, 9152),
    (1, 9161), (1, 9162),
    (1, 9171), (1, 9172),
    (1, 9181), (1, 9182),
    (2, 9151),
    (2, 9161),
    (2, 9171),
    (2, 9181)
on duplicate key update role_id = role_id;

