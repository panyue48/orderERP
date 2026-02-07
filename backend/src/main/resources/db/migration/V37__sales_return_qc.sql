-- Sales (Stage 5 P1 hardening): add QC workflow for sales returns.
-- - Step1: receive return into QC bucket (wms_stock_qc)
-- - Step2: QC pass -> stock-in + WMS bill(type=6)
-- - Step3: QC reject -> remove from QC bucket, record disposition
--
-- Safe for already-initialized databases:
-- - use dynamic SQL to add missing columns
-- - seed sys_menu / sys_role_menu idempotently

-- ==========================================
-- 1) sal_return: add QC fields
-- ==========================================

set @col_exists := (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'sal_return'
    and column_name = 'receive_by'
);
set @sql := if(@col_exists = 0,
  'alter table sal_return add column receive_by varchar(64) default null comment ''receive operator'' after audit_time',
  'select 1'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @col_exists := (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'sal_return'
    and column_name = 'receive_time'
);
set @sql := if(@col_exists = 0,
  'alter table sal_return add column receive_time datetime default null comment ''receive time'' after receive_by',
  'select 1'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @col_exists := (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'sal_return'
    and column_name = 'qc_by'
);
set @sql := if(@col_exists = 0,
  'alter table sal_return add column qc_by varchar(64) default null comment ''qc operator'' after receive_time',
  'select 1'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @col_exists := (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'sal_return'
    and column_name = 'qc_time'
);
set @sql := if(@col_exists = 0,
  'alter table sal_return add column qc_time datetime default null comment ''qc time'' after qc_by',
  'select 1'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @col_exists := (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'sal_return'
    and column_name = 'qc_disposition'
);
set @sql := if(@col_exists = 0,
  'alter table sal_return add column qc_disposition varchar(32) default null comment ''SCRAP/REPAIR/RETURN_TO_CUSTOMER'' after qc_time',
  'select 1'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @col_exists := (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'sal_return'
    and column_name = 'qc_remark'
);
set @sql := if(@col_exists = 0,
  'alter table sal_return add column qc_remark varchar(512) default null comment ''qc remark'' after qc_disposition',
  'select 1'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

-- ==========================================
-- 2) sys_menu perms (admin only)
-- ==========================================

insert into sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible)
values
    (9435, 9403, '退货单收货(待检)', null, null, 'sal:return:receive', null, 'F', 5, 0),
    (9436, 9403, '退货单质检不合格', null, null, 'sal:return:qc-reject', null, 'F', 6, 0)
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
    (1, 9435),
    (1, 9436)
on duplicate key update role_id = role_id;

