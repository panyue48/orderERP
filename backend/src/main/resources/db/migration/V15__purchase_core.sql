-- Purchase (Stage 4): purchase orders + inbound stock (integrate with WMS).
-- Safe for already-initialized databases:
-- - create tables if not exists
-- - seed sys_menu / sys_role_menu with high ids

-- ==========================================
-- 4. Purchase tables
-- ==========================================

create table if not exists pur_order (
    id bigint not null auto_increment,
    order_no varchar(64) not null comment '采购单号 (PO+日期+流水)',
    supplier_id bigint not null comment '供应商ID',
    order_date date not null comment '单据日期',
    total_amount decimal(16, 2) not null default 0.00 comment '订单总额',
    pay_amount decimal(16, 2) default 0.00 comment '已付金额',
    status tinyint default 1 comment '状态 1待审核 2已审核 3部分入库 4已完成 9已作废',
    remark varchar(512) default null,
    create_by varchar(64) default null comment '制单人',
    create_time datetime default current_timestamp,
    audit_by varchar(64) default null comment '审核人',
    audit_time datetime default null,
    primary key (id),
    unique key uk_pur_order_no (order_no)
) engine=InnoDB default charset=utf8mb4 comment='采购订单主表';

create table if not exists pur_order_detail (
    id bigint not null auto_increment,
    order_id bigint not null comment '主表ID',
    product_id bigint not null comment '商品ID',
    product_code varchar(64) default null,
    product_name varchar(128) default null,
    unit varchar(32) default null,
    price decimal(16, 2) not null default 0.00 comment '采购单价',
    qty decimal(16, 3) not null default 0.000 comment '采购数量',
    amount decimal(16, 2) not null default 0.00 comment '金额 (price*qty)',
    in_qty decimal(16, 3) default 0.000 comment '已入库数量',
    primary key (id),
    key idx_pur_order_detail_order_id (order_id)
) engine=InnoDB default charset=utf8mb4 comment='采购订单明细表';

-- ==========================================
-- System menu seed (PURCHASE)
-- ==========================================

insert into sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible)
values
    (9300, 0, '采购管理', '/purchase', 'RouteView', null, 'ShoppingCart', 'M', 6, 1),
    (9301, 9300, '采购订单', '/purchase/orders', 'views/PurchaseOrders.vue', 'pur:order:view', 'DocumentAdd', 'C', 1, 1)
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
    (9311, 9301, '采购单新增', null, null, 'pur:order:add', null, 'F', 1, 0),
    (9312, 9301, '采购单审核', null, null, 'pur:order:audit', null, 'F', 2, 0),
    (9313, 9301, '采购单入库', null, null, 'pur:order:inbound', null, 'F', 3, 0),
    (9314, 9301, '采购单作废', null, null, 'pur:order:cancel', null, 'F', 4, 0)
as new
on duplicate key update
    parent_id = new.parent_id,
    menu_name = new.menu_name,
    perms = new.perms,
    menu_type = new.menu_type,
    sort = new.sort,
    visible = new.visible;

-- Grants:
-- - admin (role_id=1): view + add/audit/inbound/cancel
-- - manager/sale_mgr (role_id=2): view only
insert into sys_role_menu (role_id, menu_id)
values
    (1, 9300), (1, 9301), (1, 9311), (1, 9312), (1, 9313), (1, 9314),
    (2, 9300), (2, 9301)
on duplicate key update role_id = role_id;

