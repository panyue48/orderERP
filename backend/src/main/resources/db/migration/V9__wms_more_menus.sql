-- WMS Core (Stage 3) menu/perms additions: stock-out + stock logs.

-- Add stock-out and log menu items under WMS (id=9200 created in V8).
insert into sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible)
values
    (9203, 9200, '手工出库', '/wms/stock-out', 'views/WmsStockOutBills.vue', 'wms:stockout:view', 'DocumentDelete', 'C', 3, 1),
    (9204, 9200, '库存流水', '/wms/stock-log', 'views/WmsStockLogs.vue', 'wms:stocklog:view', 'Document', 'C', 4, 1)
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

-- Button-level perms for stock-out (admin only)
insert into sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible)
values
    (9221, 9203, '出库新增', null, null, 'wms:stockout:add', null, 'F', 1, 0),
    (9222, 9203, '出库执行', null, null, 'wms:stockout:execute', null, 'F', 2, 0)
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
    (1, 9203), (1, 9204), (1, 9221), (1, 9222),
    (2, 9203), (2, 9204)
on duplicate key update role_id = role_id;

