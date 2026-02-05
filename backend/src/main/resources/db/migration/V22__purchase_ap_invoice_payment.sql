-- Stage 4 (Purchase) P1 extension: reconciliation (AP), invoice, payment.
-- Safe for already-initialized databases:
-- - create tables if not exists
-- - seed sys_menu / sys_role_menu with high ids

-- ==========================================
-- 4+. Purchase AP (statement) tables
-- ==========================================

create table if not exists pur_ap_bill (
    id bigint not null auto_increment,
    bill_no varchar(64) not null comment '对账单号',
    supplier_id bigint not null comment '供应商ID',
    start_date date not null comment '对账起始日期（含）',
    end_date date not null comment '对账截止日期（含）',
    total_amount decimal(16, 2) not null default 0.00 comment '对账金额（入库-退货）',
    paid_amount decimal(16, 2) not null default 0.00 comment '已付金额',
    invoice_amount decimal(16, 2) not null default 0.00 comment '已开票金额',
    status tinyint default 1 comment '状态：1草稿 2已审核 3部分已付 4已结清 9已作废',
    remark varchar(512) default null,
    create_by varchar(64) default null,
    create_time datetime default current_timestamp,
    audit_by varchar(64) default null,
    audit_time datetime default null,
    update_time datetime default current_timestamp on update current_timestamp,
    primary key (id),
    unique key uk_pur_ap_bill_no (bill_no),
    key idx_pur_ap_bill_supplier (supplier_id),
    key idx_pur_ap_bill_time (create_time)
) engine=InnoDB default charset=utf8mb4 comment='采购对账单（应付）';

create table if not exists pur_ap_bill_detail (
    id bigint not null auto_increment,
    bill_id bigint not null comment '对账单ID',
    doc_type tinyint not null comment '单据类型：1入库批次 2退货单',
    doc_id bigint not null comment '单据ID',
    doc_no varchar(64) not null comment '单据号快照',
    order_id bigint default null comment '采购单ID（入库批次才有）',
    order_no varchar(64) default null comment '采购单号快照',
    doc_time datetime default null comment '单据执行时间快照',
    amount decimal(16, 2) not null default 0.00 comment '金额（入库为正，退货为负）',
    primary key (id),
    key idx_pur_ap_bill_detail_bill (bill_id),
    key idx_pur_ap_bill_detail_doc (doc_type, doc_id)
) engine=InnoDB default charset=utf8mb4 comment='采购对账单明细（按单据汇总）';

-- Active doc lock table: ensure a business doc can be bound to at most one *active* AP bill.
create table if not exists pur_ap_doc_ref (
    id bigint not null auto_increment,
    doc_type tinyint not null comment '1入库批次 2退货单',
    doc_id bigint not null comment 'doc id',
    bill_id bigint not null comment 'ap bill id',
    create_time datetime default current_timestamp,
    primary key (id),
    unique key uk_pur_ap_doc_ref (doc_type, doc_id),
    key idx_pur_ap_doc_ref_bill (bill_id)
) engine=InnoDB default charset=utf8mb4 comment='AP 单据占用表（避免重复对账）';

create table if not exists pur_ap_payment (
    id bigint not null auto_increment,
    pay_no varchar(64) not null comment '付款单号',
    bill_id bigint not null comment '对账单ID',
    supplier_id bigint not null comment '供应商ID快照',
    pay_date date not null comment '付款日期',
    amount decimal(16, 2) not null default 0.00 comment '付款金额',
    method varchar(64) default null comment '付款方式',
    remark varchar(512) default null,
    status tinyint default 2 comment '状态：2已完成 9已作废',
    create_by varchar(64) default null,
    create_time datetime default current_timestamp,
    cancel_by varchar(64) default null,
    cancel_time datetime default null,
    primary key (id),
    unique key uk_pur_ap_payment_no (pay_no),
    key idx_pur_ap_payment_bill (bill_id),
    key idx_pur_ap_payment_supplier (supplier_id)
) engine=InnoDB default charset=utf8mb4 comment='采购付款单（登记）';

create table if not exists pur_ap_invoice (
    id bigint not null auto_increment,
    invoice_no varchar(64) not null comment '发票号',
    bill_id bigint not null comment '对账单ID',
    supplier_id bigint not null comment '供应商ID快照',
    invoice_date date not null comment '开票日期',
    amount decimal(16, 2) not null default 0.00 comment '价税合计',
    tax_amount decimal(16, 2) not null default 0.00 comment '税额（可选）',
    remark varchar(512) default null,
    status tinyint default 2 comment '状态：2有效 9作废',
    create_by varchar(64) default null,
    create_time datetime default current_timestamp,
    cancel_by varchar(64) default null,
    cancel_time datetime default null,
    primary key (id),
    unique key uk_pur_ap_invoice_no (invoice_no),
    key idx_pur_ap_invoice_bill (bill_id),
    key idx_pur_ap_invoice_supplier (supplier_id)
) engine=InnoDB default charset=utf8mb4 comment='采购发票登记';

-- ==========================================
-- System menu seed (PURCHASE AP)
-- ==========================================

insert into sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible)
values
    (9304, 9300, '采购对账单', '/purchase/ap-bills', 'views/PurchaseApBills.vue', 'pur:ap:view', 'Tickets', 'C', 4, 1)
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
    (9341, 9304, '对账单新增', null, null, 'pur:ap:add', null, 'F', 1, 0),
    (9342, 9304, '对账单审核', null, null, 'pur:ap:audit', null, 'F', 2, 0),
    (9343, 9304, '付款登记', null, null, 'pur:ap:pay', null, 'F', 3, 0),
    (9344, 9304, '发票登记', null, null, 'pur:ap:invoice', null, 'F', 4, 0),
    (9345, 9304, '对账单作废', null, null, 'pur:ap:cancel', null, 'F', 5, 0)
as new
on duplicate key update
    parent_id = new.parent_id,
    menu_name = new.menu_name,
    perms = new.perms,
    menu_type = new.menu_type,
    sort = new.sort,
    visible = new.visible;

-- Grants:
-- - admin (role_id=1): view + add/audit/pay/invoice/cancel
-- - manager/sale_mgr (role_id=2): view only
insert into sys_role_menu (role_id, menu_id)
values
    (1, 9304), (1, 9341), (1, 9342), (1, 9343), (1, 9344), (1, 9345),
    (2, 9304)
on duplicate key update role_id = role_id;

