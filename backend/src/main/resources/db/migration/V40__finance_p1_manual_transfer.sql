-- Stage 6 (Finance) P1: manual receipts/payments + internal transfers

-- 1) Allow partner_id to be null for internal transfer/manual flows
alter table fin_payment modify partner_id bigint null comment '往来单位ID（可空：内部转账等）';

-- 2) Manual receipts/payments
create table if not exists fin_manual_payment (
    id bigint not null auto_increment,
    manual_no varchar(64) not null comment '手工收付款单号',
    type tinyint not null comment '1收款 2付款',
    partner_id bigint null comment '往来单位ID（可空）',
    account_id bigint not null comment '资金账户ID',
    amount decimal(16, 2) not null comment '金额',
    pay_date date not null comment '交易日期',
    method varchar(64) null comment '方式（银行/现金等）',
    biz_no varchar(64) null comment '外部关联号（可选）',
    remark varchar(255) null,
    status tinyint not null default 1 comment '1完成 9作废',
    create_by varchar(64) null,
    create_time datetime default current_timestamp,
    cancel_by varchar(64) null,
    cancel_time datetime null,
    primary key (id),
    unique key uk_fin_manual_payment_no (manual_no),
    key idx_fin_manual_payment_date (pay_date),
    key idx_fin_manual_payment_account (account_id),
    key idx_fin_manual_payment_partner (partner_id)
) engine=InnoDB default charset=utf8mb4 comment='手工收付款';

-- 3) Internal transfer
create table if not exists fin_transfer (
    id bigint not null auto_increment,
    transfer_no varchar(64) not null comment '调拨单号',
    from_account_id bigint not null comment '转出账户',
    to_account_id bigint not null comment '转入账户',
    amount decimal(16, 2) not null comment '金额',
    transfer_date date not null comment '调拨日期',
    remark varchar(255) null,
    status tinyint not null default 1 comment '1完成 9作废',
    create_by varchar(64) null,
    create_time datetime default current_timestamp,
    cancel_by varchar(64) null,
    cancel_time datetime null,
    primary key (id),
    unique key uk_fin_transfer_no (transfer_no),
    key idx_fin_transfer_date (transfer_date),
    key idx_fin_transfer_from (from_account_id),
    key idx_fin_transfer_to (to_account_id)
) engine=InnoDB default charset=utf8mb4 comment='资金调拨';

-- ==========================================
-- Permissions (admin only)
-- ==========================================

insert into sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible)
values
    (9521, 9502, '手工收付款', null, null, 'fin:payment:manual', null, 'F', 1, 0),
    (9522, 9502, '资金调拨新增', null, null, 'fin:transfer:add', null, 'F', 2, 0),
    (9523, 9502, '资金调拨作废', null, null, 'fin:transfer:cancel', null, 'F', 3, 0)
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
    (1, 9521), (1, 9522), (1, 9523)
on duplicate key update role_id = role_id;

