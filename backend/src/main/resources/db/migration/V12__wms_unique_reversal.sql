-- WMS Core (Stage 3) hardening: prevent duplicate reversal bills under concurrency.
-- Enforce: for a given biz_id + type, only one row can exist.
--
-- Notes:
-- - In MySQL, UNIQUE indexes allow multiple NULLs; regular (non-reversal) bills keep biz_id = NULL.
-- - Use information_schema to make it idempotent.

set @idx_exists := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'wms_io_bill'
    and index_name = 'uk_wms_io_bill_biz_type'
);

set @sql := if(@idx_exists = 0,
  'create unique index uk_wms_io_bill_biz_type on wms_io_bill(biz_id, type)',
  'select 1'
);

prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;
