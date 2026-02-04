-- Purchase (Stage 4+) hardening: partial inbound via inbound documents (pur_inbound).
-- Safe for already-initialized databases:
-- - create tables if not exists
-- - seed sys_menu / sys_role_menu with high ids

-- ==========================================
-- 4+. Purchase inbound tables
-- ==========================================

create table if not exists pur_inbound (
    id bigint not null auto_increment,
    inbound_no varchar(64) not null comment '采购入库单号',
    request_no varchar(64) not null comment '幂等请求号（客户端生成UUID）',
    order_id bigint not null comment '采购订单ID',
    order_no varchar(64) not null comment '采购订单号快照',
    supplier_id bigint not null comment '供应商ID快照',
    warehouse_id bigint not null comment '入库仓库ID',
    status tinyint default 2 comment '状态 1待执行 2已完成 9已作废',
    wms_bill_id bigint default null comment '关联WMS单据ID',
    wms_bill_no varchar(64) default null comment '关联WMS单据号',
    remark varchar(512) default null,
    create_by varchar(64) default null comment '创建人',
    create_time datetime default current_timestamp,
    execute_by varchar(64) default null comment '执行人',
    execute_time datetime default null,
    primary key (id),
    unique key uk_pur_inbound_no (inbound_no),
    unique key uk_pur_inbound_request_no (request_no),
    key idx_pur_inbound_order_id (order_id)
) engine=InnoDB default charset=utf8mb4 comment='采购入库单主表（分批入库）';

create table if not exists pur_inbound_detail (
    id bigint not null auto_increment,
    inbound_id bigint not null comment '入库单ID',
    product_id bigint not null comment '商品ID',
    product_code varchar(64) default null,
    product_name varchar(128) default null,
    unit varchar(32) default null,
    plan_qty decimal(16, 3) not null default 0.000 comment '计划入库数量',
    real_qty decimal(16, 3) default 0.000 comment '实际入库数量',
    primary key (id),
    unique key uk_pur_inbound_detail (inbound_id, product_id),
    key idx_pur_inbound_detail_inbound_id (inbound_id)
) engine=InnoDB default charset=utf8mb4 comment='采购入库单明细表';

-- ==========================================
-- System menu seed (PURCHASE INBOUND)
-- ==========================================

insert into sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible)
values
    (9302, 9300, '采购入库单', '/purchase/inbounds', 'views/PurchaseInbounds.vue', 'pur:inbound:view', 'Box', 'C', 2, 1)
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

-- Button-level perms
insert into sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible)
values
    (9321, 9302, '采购入库单查看', null, null, 'pur:inbound:view', null, 'F', 1, 0)
as new
on duplicate key update
    parent_id = new.parent_id,
    menu_name = new.menu_name,
    perms = new.perms,
    menu_type = new.menu_type,
    sort = new.sort,
    visible = new.visible;

-- Grants:
-- - admin (role_id=1): view
-- - manager/sale_mgr (role_id=2): view
insert into sys_role_menu (role_id, menu_id)
values
    (1, 9302), (1, 9321),
    (2, 9302), (2, 9321)
on duplicate key update role_id = role_id;

