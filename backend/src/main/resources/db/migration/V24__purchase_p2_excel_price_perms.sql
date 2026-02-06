-- Stage 4 (Purchase) P2 engineering: Excel export/import + price view/edit perms.

-- Purchase order Excel perms (under "采购订单(记录)" menu id=9301)
insert into sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible)
values
    (9315, 9301, '采购单导出', null, null, 'pur:order:export', null, 'F', 10, 0),
    (9316, 9301, '采购单导入', null, null, 'pur:order:import', null, 'F', 11, 0)
as new
on duplicate key update
    parent_id = new.parent_id,
    menu_name = new.menu_name,
    perms = new.perms,
    menu_type = new.menu_type,
    sort = new.sort,
    visible = new.visible;

-- Price view/edit perms (under purchase root id=9300)
insert into sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible)
values
    (9391, 9300, '采购单价可见', null, null, 'pur:price:view', null, 'F', 90, 0),
    (9392, 9300, '采购单价可编辑', null, null, 'pur:price:edit', null, 'F', 91, 0)
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
    (1, 9315), (1, 9316), (1, 9391), (1, 9392)
on duplicate key update role_id = role_id;

