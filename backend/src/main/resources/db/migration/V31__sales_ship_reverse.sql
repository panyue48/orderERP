-- Sales (Stage 5 P1): shipment reversal (发货冲销/红冲) for correcting wrong shipments.
-- - Add reverse fields to sal_ship (idempotent via information_schema)
-- - Add permission point for reverse action under "销售出库单" (menu_id=9402)

-- ==========================================
-- Alter sal_ship: add reverse columns
-- ==========================================

set @col_exists := (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'sal_ship'
    and column_name = 'reverse_status'
);
set @sql := if(@col_exists = 0,
  'alter table sal_ship add column reverse_status tinyint default 0 comment ''冲销状态：0未冲销 1已冲销''',
  'select 1'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @col_exists := (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'sal_ship'
    and column_name = 'reverse_by'
);
set @sql := if(@col_exists = 0,
  'alter table sal_ship add column reverse_by varchar(64) default null comment ''冲销人''',
  'select 1'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @col_exists := (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'sal_ship'
    and column_name = 'reverse_time'
);
set @sql := if(@col_exists = 0,
  'alter table sal_ship add column reverse_time datetime default null comment ''冲销时间''',
  'select 1'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @col_exists := (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'sal_ship'
    and column_name = 'reverse_wms_bill_id'
);
set @sql := if(@col_exists = 0,
  'alter table sal_ship add column reverse_wms_bill_id bigint default null comment ''冲销关联WMS单据ID''',
  'select 1'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @col_exists := (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'sal_ship'
    and column_name = 'reverse_wms_bill_no'
);
set @sql := if(@col_exists = 0,
  'alter table sal_ship add column reverse_wms_bill_no varchar(64) default null comment ''冲销关联WMS单据号''',
  'select 1'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

-- ==========================================
-- Menu perms: reverse button under "销售出库单" (menu_id=9402)
-- ==========================================

insert into sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible)
values
    (9422, 9402, '销售出库冲销', null, null, 'sal:ship:reverse', null, 'F', 2, 0)
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
    (1, 9422)
on duplicate key update role_id = role_id;

