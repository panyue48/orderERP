-- Stage 6 (Finance) P0: fin_account + fin_payment
-- Goal: record cashflow and maintain account balances (simple, enterprise-friendly).

create table if not exists fin_account (
    id bigint not null auto_increment,
    account_name varchar(64) not null comment '账户名称',
    account_no varchar(64) null comment '账号/卡号/支付宝号',
    balance decimal(16, 2) not null default 0.00 comment '当前余额',
    remark varchar(255) null,
    status tinyint not null default 1 comment '1启用 0停用',
    deleted tinyint not null default 0,
    create_by varchar(64) null,
    create_time datetime default current_timestamp,
    update_by varchar(64) null,
    update_time datetime default current_timestamp on update current_timestamp,
    primary key (id),
    unique key uk_fin_account_name (account_name)
) engine=InnoDB default charset=utf8mb4 comment='资金账户表';

create table if not exists fin_payment (
    id bigint not null auto_increment,
    pay_no varchar(64) not null comment '流水号',
    type tinyint not null comment '1收款 2付款',
    partner_id bigint not null comment '往来单位ID',
    account_id bigint not null comment '资金账户ID',
    amount decimal(16, 2) not null comment '金额',
    biz_type tinyint not null comment '来源类型：1销售收款 2采购付款',
    biz_id bigint not null comment '来源记录ID（收款记录/付款记录）',
    biz_no varchar(64) null comment '关联单据号（如 AR/AP 对账单号）',
    pay_date date not null comment '交易日期',
    method varchar(64) null comment '方式（银行/现金等）',
    remark varchar(255) null,
    status tinyint not null default 1 comment '1完成 9作废',
    create_by varchar(64) null,
    create_time datetime default current_timestamp,
    cancel_by varchar(64) null,
    cancel_time datetime null,
    primary key (id),
    unique key uk_fin_payment_pay_no (pay_no),
    unique key uk_fin_payment_biz (biz_type, biz_id),
    key idx_fin_payment_partner (partner_id),
    key idx_fin_payment_account (account_id),
    key idx_fin_payment_date (pay_date)
) engine=InnoDB default charset=utf8mb4 comment='收付款流水（资金变动）';

-- Seed a default account for easy local testing
insert into fin_account (account_name, account_no, balance, remark, status, deleted)
select '公司基本户', null, 1000000.00, 'seed', 1, 0
where not exists (
    select 1 from fin_account a where a.account_name = '公司基本户' and a.deleted = 0
);

-- ==========================================
-- System menu seed (FINANCE)
-- ==========================================

insert into sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible)
values
    (9500, 0, '财务管理', '/finance', 'RouteView', null, 'Coin', 'M', 8, 1),
    (9501, 9500, '资金账户', '/finance/accounts', 'views/FinanceAccounts.vue', 'fin:account:view', 'Wallet', 'C', 1, 1),
    (9502, 9500, '收付款流水', '/finance/payments', 'views/FinancePayments.vue', 'fin:payment:view', 'List', 'C', 2, 1)
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
    (9511, 9501, '资金账户新增', null, null, 'fin:account:add', null, 'F', 1, 0)
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
    (1, 9500), (1, 9501), (1, 9502), (1, 9511),
    (2, 9500), (2, 9501), (2, 9502)
on duplicate key update role_id = role_id;
