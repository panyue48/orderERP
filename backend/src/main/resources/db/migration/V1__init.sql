-- One-shot bootstrap migration for MySQL.
-- Includes: ledger (demo), system core (RBAC), and menu component mappings for frontend routing.

create table if not exists ledger_entry (
    id bigint primary key auto_increment,
    entry_date date not null,
    account_code varchar(64) not null,
    description varchar(255),
    debit decimal(19, 2) not null default 0,
    credit decimal(19, 2) not null default 0,
    created_at timestamp not null default current_timestamp,
    index idx_ledger_entry_date (entry_date),
    index idx_ledger_entry_account_code (account_code)
);

-- ==========================================
-- System core (SYS / RBAC)
-- ==========================================

create table if not exists sys_user (
    id bigint not null auto_increment,
    username varchar(50) not null,
    password varchar(100) not null,
    nickname varchar(50),
    email varchar(100),
    phone varchar(20),
    avatar varchar(255),
    status tinyint default 1,
    deleted tinyint default 0,
    create_time datetime default current_timestamp,
    update_time datetime default current_timestamp on update current_timestamp,
    primary key (id),
    unique key uk_sys_user_username (username)
) engine=InnoDB default charset=utf8mb4;

create table if not exists sys_role (
    id bigint not null auto_increment,
    role_name varchar(50) not null,
    role_key varchar(50) not null,
    sort int default 0,
    status tinyint default 1,
    deleted tinyint default 0,
    create_time datetime default current_timestamp,
    primary key (id),
    unique key uk_sys_role_key (role_key)
) engine=InnoDB default charset=utf8mb4;

create table if not exists sys_menu (
    id bigint not null auto_increment,
    parent_id bigint default 0,
    menu_name varchar(50) not null,
    path varchar(200),
    component varchar(255),
    perms varchar(100),
    icon varchar(100),
    menu_type char(1) default 'M',
    sort int default 0,
    visible tinyint default 1,
    primary key (id)
) engine=InnoDB default charset=utf8mb4;

create table if not exists sys_user_role (
    user_id bigint not null,
    role_id bigint not null,
    primary key (user_id, role_id)
) engine=InnoDB default charset=utf8mb4;

create table if not exists sys_role_menu (
    role_id bigint not null,
    menu_id bigint not null,
    primary key (role_id, menu_id)
) engine=InnoDB default charset=utf8mb4;

-- Seed users (dev only). Password is plain "123456" for compatibility; backend will treat it as {noop}.
insert into sys_user (id, username, password, nickname, status, deleted)
values
    (1, 'admin', '123456', '系统管理员', 1, 0),
    (2, 'manager', '123456', '张经理', 1, 0),
    (3, 'staff', '123456', '李员工', 1, 0)
on duplicate key update
    username = values(username),
    password = values(password),
    nickname = values(nickname),
    status = values(status),
    deleted = values(deleted);

insert into sys_role (id, role_name, role_key, sort, status, deleted)
values
    (1, '超级管理员', 'admin', 1, 1, 0),
    (2, '销售经理', 'sale_mgr', 2, 1, 0)
on duplicate key update
    role_name = values(role_name),
    role_key = values(role_key),
    sort = values(sort),
    status = values(status),
    deleted = values(deleted);

insert into sys_user_role (user_id, role_id)
values (1, 1), (2, 2)
on duplicate key update user_id = user_id;

-- Ensure the frontend-required menus exist and have correct component mapping.
-- Use high ids to avoid clashing with existing datasets like erp_data.sql.
insert into sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible)
values
    (9001, 0, '概览', '/dashboard', 'views/Dashboard.vue', 'dashboard:view', 'Odometer', 'C', 1, 1),
    (9002, 0, '记账', '/ledger', 'views/Ledger.vue', 'ledger:view', 'Notebook', 'C', 2, 1),
    (9003, 0, '系统管理', '/system', 'RouteView', null, 'Setting', 'M', 3, 1),
    (9004, 9003, '用户管理', '/system/users', 'views/Placeholder.vue', 'sys:user:view', 'User', 'C', 1, 1),
    (9005, 9003, '角色管理', '/system/roles', 'views/Placeholder.vue', 'sys:role:view', 'UserFilled', 'C', 2, 1)
on duplicate key update
    parent_id = values(parent_id),
    menu_name = values(menu_name),
    path = values(path),
    component = values(component),
    perms = values(perms),
    icon = values(icon),
    menu_type = values(menu_type),
    sort = values(sort),
    visible = values(visible);

-- Also fix component mapping for existing menus (if they exist with null component).
update sys_menu set component = 'views/Dashboard.vue' where path = '/dashboard' and (component is null or component = '');
update sys_menu set component = 'views/Ledger.vue' where path = '/ledger' and (component is null or component = '');

insert into sys_role_menu (role_id, menu_id)
values
    (1, 9001), (1, 9002), (1, 9003), (1, 9004), (1, 9005),
    (2, 9001), (2, 9002)
on duplicate key update role_id = role_id;
