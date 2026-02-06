-- Sales (Stage 5 - P0): sales order -> lock stock -> ship (deduct stock).
-- Safe for already-initialized databases:
-- - create tables if not exists
-- - seed sys_menu / sys_role_menu with high ids

-- ==========================================
-- Fix WMS bill type comment (align with current code usage)
-- ==========================================
-- 1 purchase in, 2 purchase return, 3 stock in, 4 stock out (and its reversal), 5 sales out
alter table wms_io_bill
    modify column type tinyint not null comment '1 purchase in, 2 purchase return, 3 stock in, 4 stock out, 5 sales out';

-- ==========================================
-- 5. Sales tables
-- ==========================================

create table if not exists sal_order (
    id bigint not null auto_increment,
    order_no varchar(64) not null comment 'sales order no',
    customer_id bigint not null comment 'customer partner id',
    customer_code varchar(64) default null,
    customer_name varchar(128) default null,
    warehouse_id bigint not null comment 'ship from warehouse id',
    order_date date not null comment 'order date',
    total_amount decimal(16, 2) not null default 0.00 comment 'order total amount',
    status tinyint default 1 comment '1 draft, 2 audited(locked), 4 shipped, 9 canceled',
    remark varchar(512) default null,
    wms_bill_id bigint default null comment 'related wms bill id (sales out)',
    wms_bill_no varchar(64) default null comment 'related wms bill no',
    create_by varchar(64) default null,
    create_time datetime default current_timestamp,
    audit_by varchar(64) default null,
    audit_time datetime default null,
    ship_by varchar(64) default null,
    ship_time datetime default null,
    cancel_by varchar(64) default null,
    cancel_time datetime default null,
    primary key (id),
    unique key uk_sal_order_no (order_no)
) engine=InnoDB default charset=utf8mb4 comment='sales order header';

create table if not exists sal_order_detail (
    id bigint not null auto_increment,
    order_id bigint not null,
    product_id bigint not null,
    product_code varchar(64) default null,
    product_name varchar(128) default null,
    unit varchar(32) default null,
    price decimal(16, 2) default null,
    qty decimal(16, 3) not null default 0.000,
    amount decimal(16, 2) default null,
    primary key (id),
    key idx_sal_order_detail_order_id (order_id)
) engine=InnoDB default charset=utf8mb4 comment='sales order detail';

-- ==========================================
-- System menu seed (SALES)
-- ==========================================

insert into sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible)
values
    (9400, 0, '销售管理', '/sales', 'RouteView', null, 'Sell', 'M', 7, 1),
    (9401, 9400, '销售订单', '/sales/orders', 'views/SalesOrders.vue', 'sal:order:view', 'Tickets', 'C', 1, 1)
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

-- Button-level perms (admin only)
insert into sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible)
values
    (9411, 9401, '销售订单新增', null, null, 'sal:order:add', null, 'F', 1, 0),
    (9412, 9401, '销售订单审核', null, null, 'sal:order:audit', null, 'F', 2, 0),
    (9413, 9401, '销售订单发货', null, null, 'sal:order:ship', null, 'F', 3, 0),
    (9414, 9401, '销售订单作废', null, null, 'sal:order:cancel', null, 'F', 4, 0)
as new
on duplicate key update
    parent_id = new.parent_id,
    menu_name = new.menu_name,
    perms = new.perms,
    menu_type = new.menu_type,
    sort = new.sort,
    visible = new.visible;

-- Grants:
-- - admin (role_id=1): view + add/audit/ship/cancel
-- - manager/sale_mgr (role_id=2): view only
insert into sys_role_menu (role_id, menu_id)
values
    (1, 9400), (1, 9401), (1, 9411), (1, 9412), (1, 9413), (1, 9414),
    (2, 9400), (2, 9401)
on duplicate key update role_id = role_id;

