-- Purchase (Stage 4+) P1: purchase return to supplier (pur_return).
-- Safe for already-initialized databases:
-- - create tables if not exists
-- - seed sys_menu / sys_role_menu with high ids

-- ==========================================
-- 4+. Purchase return tables
-- ==========================================

create table if not exists pur_return (
    id bigint not null auto_increment,
    return_no varchar(64) not null comment '采购退货单号',
    supplier_id bigint not null comment '供应商ID',
    warehouse_id bigint not null comment '退货仓库ID',
    return_date date not null comment '单据日期',
    total_amount decimal(16, 2) not null default 0.00 comment '退货总额',
    status tinyint default 1 comment '状态 1待审核 2已审核 4已完成 9已作废',
    wms_bill_id bigint default null comment '关联WMS单据ID',
    wms_bill_no varchar(64) default null comment '关联WMS单据号',
    remark varchar(512) default null,
    create_by varchar(64) default null comment '制单人',
    create_time datetime default current_timestamp,
    audit_by varchar(64) default null comment '审核人',
    audit_time datetime default null,
    execute_by varchar(64) default null comment '执行人',
    execute_time datetime default null,
    primary key (id),
    unique key uk_pur_return_no (return_no),
    key idx_pur_return_supplier_id (supplier_id),
    key idx_pur_return_warehouse_id (warehouse_id)
) engine=InnoDB default charset=utf8mb4 comment='采购退货单主表';

create table if not exists pur_return_detail (
    id bigint not null auto_increment,
    return_id bigint not null comment '主表ID',
    product_id bigint not null comment '商品ID',
    product_code varchar(64) default null,
    product_name varchar(128) default null,
    unit varchar(32) default null,
    price decimal(16, 2) not null default 0.00 comment '退货单价',
    qty decimal(16, 3) not null default 0.000 comment '退货数量',
    amount decimal(16, 2) not null default 0.00 comment '金额 (price*qty)',
    primary key (id),
    unique key uk_pur_return_detail (return_id, product_id),
    key idx_pur_return_detail_return_id (return_id)
) engine=InnoDB default charset=utf8mb4 comment='采购退货单明细表';

-- ==========================================
-- System menu seed (PURCHASE RETURN)
-- ==========================================

insert into sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible)
values
    (9303, 9300, '采购退货单', '/purchase/returns', 'views/PurchaseReturns.vue', 'pur:return:view', 'Back', 'C', 3, 1)
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

insert into sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible)
values
    (9331, 9303, '退货单新增', null, null, 'pur:return:add', null, 'F', 1, 0),
    (9332, 9303, '退货单审核', null, null, 'pur:return:audit', null, 'F', 2, 0),
    (9333, 9303, '退货单执行', null, null, 'pur:return:execute', null, 'F', 3, 0),
    (9334, 9303, '退货单作废', null, null, 'pur:return:cancel', null, 'F', 4, 0)
as new
on duplicate key update
    parent_id = new.parent_id,
    menu_name = new.menu_name,
    perms = new.perms,
    menu_type = new.menu_type,
    sort = new.sort,
    visible = new.visible;

-- Grants:
-- - admin (role_id=1): view + add/audit/execute/cancel
-- - manager/sale_mgr (role_id=2): view only
insert into sys_role_menu (role_id, menu_id)
values
    (1, 9303), (1, 9331), (1, 9332), (1, 9333), (1, 9334),
    (2, 9303)
on duplicate key update role_id = role_id;

