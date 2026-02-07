-- Sales (Stage 5 P1 hardening): link sales return to original shipment batch.
-- Safe for already-initialized databases:
-- - use dynamic SQL to add missing columns / indexes
-- - migrate unique keys to allow multiple lines per product if needed (by ship_detail_id)

-- ==========================================
-- 1) sal_return: add source ship/order refs
-- ==========================================

set @col_exists := (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'sal_return'
    and column_name = 'ship_id'
);
set @sql := if(@col_exists = 0,
  'alter table sal_return add column ship_id bigint default null comment ''source sal_ship id'' after warehouse_id',
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
    and column_name = 'ship_no'
);
set @sql := if(@col_exists = 0,
  'alter table sal_return add column ship_no varchar(64) default null comment ''source shipment no'' after ship_id',
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
    and column_name = 'order_id'
);
set @sql := if(@col_exists = 0,
  'alter table sal_return add column order_id bigint default null comment ''source sales order id'' after ship_no',
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
    and column_name = 'order_no'
);
set @sql := if(@col_exists = 0,
  'alter table sal_return add column order_no varchar(64) default null comment ''source sales order no'' after order_id',
  'select 1'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

-- helpful indexes
set @idx_exists := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'sal_return'
    and index_name = 'idx_sal_return_ship_id'
);
set @sql := if(@idx_exists = 0,
  'alter table sal_return add key idx_sal_return_ship_id (ship_id)',
  'select 1'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

-- ==========================================
-- 2) sal_return_detail: add ship_detail refs
-- ==========================================

set @col_exists := (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'sal_return_detail'
    and column_name = 'ship_detail_id'
);
set @sql := if(@col_exists = 0,
  'alter table sal_return_detail add column ship_detail_id bigint default null comment ''source sal_ship_detail id'' after return_id',
  'select 1'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @col_exists := (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'sal_return_detail'
    and column_name = 'order_detail_id'
);
set @sql := if(@col_exists = 0,
  'alter table sal_return_detail add column order_detail_id bigint default null comment ''source sal_order_detail id'' after ship_detail_id',
  'select 1'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @idx_exists := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'sal_return_detail'
    and index_name = 'idx_sal_return_detail_ship_detail_id'
);
set @sql := if(@idx_exists = 0,
  'alter table sal_return_detail add key idx_sal_return_detail_ship_detail_id (ship_detail_id)',
  'select 1'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

-- ==========================================
-- 3) Unique key: allow one row per ship_detail_id (not per product)
-- ==========================================

set @uk_exists := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'sal_return_detail'
    and index_name = 'uk_sal_return_detail'
);

-- Drop legacy uk (return_id, product_id) if exists, then create new uk(return_id, ship_detail_id).
set @sql := if(@uk_exists > 0,
  'alter table sal_return_detail drop index uk_sal_return_detail',
  'select 1'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @uk_exists := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'sal_return_detail'
    and index_name = 'uk_sal_return_detail_ship'
);
set @sql := if(@uk_exists = 0,
  'alter table sal_return_detail add unique key uk_sal_return_detail_ship (return_id, ship_detail_id)',
  'select 1'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

