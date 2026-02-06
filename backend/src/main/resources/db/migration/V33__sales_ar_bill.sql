-- Sales (Stage 5 P2): reconciliation (AR), invoice, receipt.
-- Safe for already-initialized databases:
-- - create tables if not exists
-- - seed sys_menu / sys_role_menu with high ids

-- ==========================================
-- 5+. Sales AR (statement) tables
-- ==========================================

create table if not exists sal_ar_bill (
    id bigint not null auto_increment,
    bill_no varchar(64) not null comment '对账单号',
    customer_id bigint not null comment '客户ID',
    start_date date not null comment '对账起始日期（含）',
    end_date date not null comment '对账截止日期（含）',
    total_amount decimal(16, 2) not null default 0.00 comment '对账金额（发货-退货）',
    received_amount decimal(16, 2) not null default 0.00 comment '已收金额',
    invoice_amount decimal(16, 2) not null default 0.00 comment '已开票金额',
    status tinyint default 1 comment '状态：1草稿 2已审核 3部分已收 4已结清 9已作废',
    remark varchar(512) default null,
    create_by varchar(64) default null,
    create_time datetime default current_timestamp,
    audit_by varchar(64) default null,
    audit_time datetime default null,
    update_time datetime default current_timestamp on update current_timestamp,
    primary key (id),
    unique key uk_sal_ar_bill_no (bill_no),
    key idx_sal_ar_bill_customer (customer_id),
    key idx_sal_ar_bill_time (create_time)
) engine=InnoDB default charset=utf8mb4 comment='销售对账单（应收）';

create table if not exists sal_ar_bill_detail (
    id bigint not null auto_increment,
    bill_id bigint not null comment '对账单ID',
    doc_type tinyint not null comment '单据类型：1发货批次 2销售退货单',
    doc_id bigint not null comment '单据ID',
    doc_no varchar(64) not null comment '单据号快照',
    order_id bigint default null comment '销售订单ID（发货批次才有）',
    order_no varchar(64) default null comment '销售订单号快照',
    doc_time datetime default null comment '单据执行时间快照',
    amount decimal(16, 2) not null default 0.00 comment '金额（发货为正，退货为负）',
    primary key (id),
    key idx_sal_ar_bill_detail_bill (bill_id),
    key idx_sal_ar_bill_detail_doc (doc_type, doc_id)
) engine=InnoDB default charset=utf8mb4 comment='销售对账单明细（按单据汇总）';

-- Active doc lock table: ensure a business doc can be bound to at most one *active* AR bill.
create table if not exists sal_ar_doc_ref (
    id bigint not null auto_increment,
    doc_type tinyint not null comment '1发货批次 2销售退货单',
    doc_id bigint not null comment 'doc id',
    bill_id bigint not null comment 'ar bill id',
    create_time datetime default current_timestamp,
    primary key (id),
    unique key uk_sal_ar_doc_ref (doc_type, doc_id),
    key idx_sal_ar_doc_ref_bill (bill_id)
) engine=InnoDB default charset=utf8mb4 comment='AR 单据占用表（避免重复对账）';

create table if not exists sal_ar_receipt (
    id bigint not null auto_increment,
    receipt_no varchar(64) not null comment '收款单号',
    bill_id bigint not null comment '对账单ID',
    customer_id bigint not null comment '客户ID快照',
    receipt_date date not null comment '收款日期',
    amount decimal(16, 2) not null default 0.00 comment '收款金额',
    method varchar(64) default null comment '收款方式',
    remark varchar(512) default null,
    status tinyint default 2 comment '状态：2已完成 9已作废',
    create_by varchar(64) default null,
    create_time datetime default current_timestamp,
    cancel_by varchar(64) default null,
    cancel_time datetime default null,
    primary key (id),
    unique key uk_sal_ar_receipt_no (receipt_no),
    key idx_sal_ar_receipt_bill (bill_id),
    key idx_sal_ar_receipt_customer (customer_id)
) engine=InnoDB default charset=utf8mb4 comment='销售收款单（登记）';

create table if not exists sal_ar_invoice (
    id bigint not null auto_increment,
    invoice_no varchar(64) not null comment '发票号',
    bill_id bigint not null comment '对账单ID',
    customer_id bigint not null comment '客户ID快照',
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
    unique key uk_sal_ar_invoice_no (invoice_no),
    key idx_sal_ar_invoice_bill (bill_id),
    key idx_sal_ar_invoice_customer (customer_id)
) engine=InnoDB default charset=utf8mb4 comment='销售发票登记';

-- ==========================================
-- System menu seed (SALES AR)
-- ==========================================

insert into sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible)
values
    (9404, 9400, '销售对账单', '/sales/ar-bills', 'views/SalesArBills.vue', 'sal:ar:view', 'Tickets', 'C', 4, 1)
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
    (9441, 9404, '对账单新增', null, null, 'sal:ar:add', null, 'F', 1, 0),
    (9442, 9404, '对账单审核', null, null, 'sal:ar:audit', null, 'F', 2, 0),
    (9443, 9404, '收款登记', null, null, 'sal:ar:recv', null, 'F', 3, 0),
    (9444, 9404, '发票登记', null, null, 'sal:ar:invoice', null, 'F', 4, 0),
    (9445, 9404, '对账单作废', null, null, 'sal:ar:cancel', null, 'F', 5, 0),
    (9446, 9404, '对账单重新生成', null, null, 'sal:ar:regen', null, 'F', 6, 0)
as new
on duplicate key update
    parent_id = new.parent_id,
    menu_name = new.menu_name,
    perms = new.perms,
    menu_type = new.menu_type,
    sort = new.sort,
    visible = new.visible;

-- Grants:
-- - admin (role_id=1): view + add/audit/recv/invoice/cancel/regen
-- - manager/sale_mgr (role_id=2): view only
insert into sys_role_menu (role_id, menu_id)
values
    (1, 9404), (1, 9441), (1, 9442), (1, 9443), (1, 9444), (1, 9445), (1, 9446),
    (2, 9404)
on duplicate key update role_id = role_id;

