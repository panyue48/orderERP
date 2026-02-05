-- Purchase (Stage 4+) P1: IQC for purchase inbound.
-- - Add IQC fields to pur_inbound (idempotent via information_schema)
-- - Add button-level perms for IQC actions

-- ==========================================
-- Alter pur_inbound: add IQC columns
-- ==========================================

set @col_exists := (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'pur_inbound'
    and column_name = 'qc_status'
);

set @sql := if(@col_exists = 0,
  'alter table pur_inbound add column qc_status tinyint default 1 comment ''质检状态 1待质检 2通过 3不合格''',
  'select 1'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @col_exists := (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'pur_inbound'
    and column_name = 'qc_by'
);
set @sql := if(@col_exists = 0,
  'alter table pur_inbound add column qc_by varchar(64) default null comment ''质检人''',
  'select 1'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @col_exists := (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'pur_inbound'
    and column_name = 'qc_time'
);
set @sql := if(@col_exists = 0,
  'alter table pur_inbound add column qc_time datetime default null comment ''质检时间''',
  'select 1'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @col_exists := (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'pur_inbound'
    and column_name = 'qc_remark'
);
set @sql := if(@col_exists = 0,
  'alter table pur_inbound add column qc_remark varchar(512) default null comment ''质检备注''',
  'select 1'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

-- ==========================================
-- Menu perms: IQC button under "采购入库单" (menu_id=9302)
-- ==========================================

insert into sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible)
values
    (9323, 9302, '采购入库质检', null, null, 'pur:inbound:iqc', null, 'F', 3, 0)
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
    (1, 9323)
on duplicate key update role_id = role_id;

