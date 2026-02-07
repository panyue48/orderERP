-- Sales (Stage 5): idempotency for "ship batch" via requestNo.
-- Safe for already-initialized databases:
-- - use dynamic SQL to add missing column / unique key

set @col_exists := (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'sal_ship'
    and column_name = 'request_no'
);
set @sql := if(@col_exists = 0,
  'alter table sal_ship add column request_no varchar(64) default null comment ''idempotency request no'' after ship_no',
  'select 1'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @idx_exists := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'sal_ship'
    and index_name = 'uk_sal_ship_request_no'
);
set @sql := if(@idx_exists = 0,
  'alter table sal_ship add unique key uk_sal_ship_request_no (request_no)',
  'select 1'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

