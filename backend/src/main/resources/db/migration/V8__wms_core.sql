-- WMS Core (Stage 3): stock ledger + manual stock-in bills.
-- Keep it safe for already-initialized databases:
-- - create tables if not exists
-- - seed sys_menu / sys_role_menu with high ids

-- ==========================================
-- 3. WMS Core tables
-- ==========================================

create table if not exists wms_stock (
    id bigint not null auto_increment,
    warehouse_id bigint not null comment 'warehouse id',
    product_id bigint not null comment 'product id',
    stock_qty decimal(16, 3) not null default 0.000 comment 'physical stock',
    locked_qty decimal(16, 3) not null default 0.000 comment 'locked stock',
    version int default 0 comment 'optimistic lock version',
    update_time datetime default current_timestamp on update current_timestamp,
    primary key (id),
    unique key uk_wms_stock_wh_prod (warehouse_id, product_id)
) engine=InnoDB default charset=utf8mb4 comment='wms stock';

create table if not exists wms_io_bill (
    id bigint not null auto_increment,
    bill_no varchar(64) not null comment 'bill no',
    type tinyint not null comment '1 purchase in, 2 sales out, 3 stock in, 4 stock out',
    biz_id bigint default null comment 'related business id',
    biz_no varchar(64) default null comment 'related business no',
    warehouse_id bigint not null comment 'warehouse id',
    status tinyint default 1 comment '1 pending, 2 completed',
    remark varchar(255) default null,
    create_by varchar(64) default null,
    create_time datetime default current_timestamp,
    primary key (id),
    unique key uk_wms_io_bill_no (bill_no)
) engine=InnoDB default charset=utf8mb4 comment='wms io bill';

create table if not exists wms_io_bill_detail (
    id bigint not null auto_increment,
    bill_id bigint not null,
    product_id bigint not null,
    qty decimal(16, 3) not null comment 'planned qty',
    real_qty decimal(16, 3) default 0.000 comment 'real qty',
    primary key (id),
    key idx_wms_io_bill_detail_bill (bill_id)
) engine=InnoDB default charset=utf8mb4 comment='wms io bill detail';

create table if not exists wms_stock_log (
    id bigint not null auto_increment,
    warehouse_id bigint not null,
    product_id bigint not null,
    biz_type varchar(32) not null comment 'biz type',
    biz_no varchar(64) default null comment 'biz no',
    change_qty decimal(16, 3) not null comment 'change qty',
    after_stock_qty decimal(16, 3) not null comment 'after stock qty',
    create_time datetime default current_timestamp,
    primary key (id),
    key idx_wms_stock_log_wh_prod (warehouse_id, product_id),
    key idx_wms_stock_log_biz_no (biz_no)
) engine=InnoDB default charset=utf8mb4 comment='wms stock log';

-- ==========================================
-- System menu seed (WMS)
-- ==========================================

insert into sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible)
values
    (9200, 0, '库存管理', '/wms', 'RouteView', null, 'Coin', 'M', 5, 1),
    (9201, 9200, '库存查询', '/wms/stock', 'views/WmsStocks.vue', 'wms:stock:view', 'List', 'C', 1, 1),
    (9202, 9200, '手工入库', '/wms/stock-in', 'views/WmsStockInBills.vue', 'wms:stockin:view', 'DocumentAdd', 'C', 2, 1)
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
    (9211, 9202, '入库新增', null, null, 'wms:stockin:add', null, 'F', 1, 0),
    (9212, 9202, '入库执行', null, null, 'wms:stockin:execute', null, 'F', 2, 0)
as new
on duplicate key update
    parent_id = new.parent_id,
    menu_name = new.menu_name,
    perms = new.perms,
    menu_type = new.menu_type,
    sort = new.sort,
    visible = new.visible;

-- Grants:
-- - admin (role_id=1): view + create/execute
-- - manager/sale_mgr (role_id=2): view only
insert into sys_role_menu (role_id, menu_id)
values
    (1, 9200), (1, 9201), (1, 9202), (1, 9211), (1, 9212),
    (2, 9200), (2, 9201), (2, 9202)
on duplicate key update role_id = role_id;

