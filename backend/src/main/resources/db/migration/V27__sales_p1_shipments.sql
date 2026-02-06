-- Sales (Stage 5 - P1): partial/multi shipments.
-- Safe for already-initialized databases:
-- - create tables if not exists
-- - seed sys_menu / sys_role_menu with high ids

-- 1=draft, 2=audited(locked), 3=partial shipped, 4=shipped, 9=canceled
alter table sal_order
    modify column status tinyint default 1 comment '1 draft, 2 audited(locked), 3 partial shipped, 4 shipped, 9 canceled';

-- MySQL doesn't support "ADD COLUMN IF NOT EXISTS" in all versions, use dynamic SQL.
set @col_exists := (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'sal_order_detail'
    and column_name = 'shipped_qty'
);

set @sql := if(@col_exists = 0,
  'alter table sal_order_detail add column shipped_qty decimal(16, 3) not null default 0.000 comment ''already shipped qty''',
  'select 1'
);

prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

create table if not exists sal_ship (
    id bigint not null auto_increment,
    ship_no varchar(64) not null comment 'shipment no',
    order_id bigint not null comment 'sales order id',
    order_no varchar(64) default null,
    customer_id bigint not null,
    customer_code varchar(64) default null,
    customer_name varchar(128) default null,
    warehouse_id bigint not null,
    ship_time datetime default current_timestamp,
    total_qty decimal(16, 3) not null default 0.000,
    wms_bill_id bigint default null,
    wms_bill_no varchar(64) default null,
    create_by varchar(64) default null,
    create_time datetime default current_timestamp,
    primary key (id),
    unique key uk_sal_ship_no (ship_no),
    key idx_sal_ship_order_id (order_id)
) engine=InnoDB default charset=utf8mb4 comment='sales shipment header';

create table if not exists sal_ship_detail (
    id bigint not null auto_increment,
    ship_id bigint not null,
    order_id bigint not null,
    order_detail_id bigint not null,
    product_id bigint not null,
    product_code varchar(64) default null,
    product_name varchar(128) default null,
    unit varchar(32) default null,
    qty decimal(16, 3) not null default 0.000,
    primary key (id),
    key idx_sal_ship_detail_ship_id (ship_id),
    key idx_sal_ship_detail_order_id (order_id)
) engine=InnoDB default charset=utf8mb4 comment='sales shipment detail';

-- Additional button perm: create partial shipment
insert into sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible)
values
    (9416, 9401, '销售订单分批发货', null, null, 'sal:order:ship-batch', null, 'F', 5, 0)
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
    (1, 9416)
on duplicate key update role_id = role_id;
