-- Base Data (Stage 2): products, warehouses, partners + menu/perms seed.
-- This migration is written to be safe on an already-initialized database:
-- - create tables if not exists
-- - add missing columns via information_schema checks (MySQL-compatible)

-- ==========================================
-- 2. Base Data (BASE)
-- ==========================================

create table if not exists base_product (
    id bigint not null auto_increment,
    category_id bigint default null comment 'category id',
    product_code varchar(64) not null comment 'sku code',
    product_name varchar(128) not null comment 'product name',
    barcode varchar(64) default null comment 'barcode',
    spec varchar(128) default null comment 'spec',
    unit varchar(32) default '个' comment 'unit',
    weight decimal(10, 3) default 0.000 comment 'weight(kg)',
    purchase_price decimal(16, 2) default 0.00 comment 'purchase price',
    sale_price decimal(16, 2) default 0.00 comment 'sale price',
    low_stock int default 10 comment 'low stock threshold',
    status tinyint default 1 comment '1 enabled, 0 disabled',
    deleted tinyint default 0 comment 'logical delete',
    create_time datetime default current_timestamp,
    update_time datetime default current_timestamp on update current_timestamp,
    primary key (id),
    unique key uk_base_product_code (product_code)
) engine=InnoDB default charset=utf8mb4;

-- Optional column used by the frontend Upload component.
-- MySQL doesn't support "ADD COLUMN IF NOT EXISTS", so we use dynamic SQL.
set @col_exists := (
  select count(*)
  from information_schema.columns
  where table_schema = database()
    and table_name = 'base_product'
    and column_name = 'image_url'
);
set @sql := if(@col_exists = 0,
  'alter table base_product add column image_url varchar(255) default null comment ''product image url'' after spec',
  'select 1'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

create table if not exists base_warehouse (
    id bigint not null auto_increment,
    warehouse_code varchar(32) not null comment 'warehouse code',
    warehouse_name varchar(64) not null comment 'warehouse name',
    location varchar(255) default null comment 'location',
    manager varchar(32) default null comment 'manager',
    status tinyint default 1,
    deleted tinyint default 0,
    create_time datetime default current_timestamp,
    update_time datetime default current_timestamp on update current_timestamp,
    primary key (id),
    unique key uk_base_warehouse_code (warehouse_code)
) engine=InnoDB default charset=utf8mb4;

create table if not exists base_partner (
    id bigint not null auto_increment,
    partner_name varchar(128) not null comment 'partner name',
    partner_code varchar(64) not null comment 'partner code',
    type tinyint not null comment '1 supplier, 2 customer',
    contact varchar(64) default null,
    phone varchar(32) default null,
    email varchar(64) default null,
    credit_limit decimal(16, 2) default 0.00,
    status tinyint default 1,
    deleted tinyint default 0,
    create_time datetime default current_timestamp,
    update_time datetime default current_timestamp on update current_timestamp,
    primary key (id),
    unique key uk_base_partner_code (partner_code)
) engine=InnoDB default charset=utf8mb4;

-- ==========================================
-- System menu seed (BASE)
-- ==========================================

-- High IDs to avoid clashing with imported datasets.
insert into sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible)
values
    (9100, 0, '基础资料', '/base', 'RouteView', null, 'Box', 'M', 4, 1),
    (9101, 9100, '商品管理', '/base/product', 'views/BaseProducts.vue', 'base:product:view', 'Goods', 'C', 1, 1),
    (9102, 9100, '仓库管理', '/base/warehouse', 'views/BaseWarehouses.vue', 'base:warehouse:view', 'OfficeBuilding', 'C', 2, 1),
    (9103, 9100, '往来单位', '/base/partner', 'views/BasePartners.vue', 'base:partner:view', 'Connection', 'C', 3, 1)
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

insert into sys_role_menu (role_id, menu_id)
values
    (1, 9100), (1, 9101), (1, 9102), (1, 9103),
    (2, 9100), (2, 9101), (2, 9102), (2, 9103)
on duplicate key update role_id = role_id;

-- ==========================================
-- Demo data (optional)
-- ==========================================

insert into base_warehouse (id, warehouse_code, warehouse_name, status, deleted)
values (1, 'WH-01', '主营仓库', 1, 0)
as new
on duplicate key update
    warehouse_code = new.warehouse_code,
    warehouse_name = new.warehouse_name,
    status = new.status,
    deleted = new.deleted;

insert into base_partner (id, partner_name, partner_code, type, contact, phone, status, deleted)
values
    (2001, '默认供应商', 'SUP-DEFAULT', 1, '张三', '13800000000', 1, 0),
    (2002, '默认客户', 'CUS-DEFAULT', 2, '李四', '13900000000', 1, 0)
as new
on duplicate key update
    partner_name = new.partner_name,
    partner_code = new.partner_code,
    type = new.type,
    contact = new.contact,
    phone = new.phone,
    status = new.status,
    deleted = new.deleted;

insert into base_product (id, product_code, product_name, unit, purchase_price, sale_price, status, deleted)
values
    (10001, 'SKU-001', '示例商品A', '个', 10.00, 15.00, 1, 0),
    (10002, 'SKU-002', '示例商品B', '个', 20.00, 29.90, 1, 0)
as new
on duplicate key update
    product_code = new.product_code,
    product_name = new.product_name,
    unit = new.unit,
    purchase_price = new.purchase_price,
    sale_price = new.sale_price,
    status = new.status,
    deleted = new.deleted;

