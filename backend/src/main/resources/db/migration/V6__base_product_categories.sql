-- Base Data (Stage 2) enhancement: product categories (optional but recommended).

create table if not exists base_product_category (
    id bigint not null auto_increment,
    parent_id bigint default 0 comment 'parent category id',
    category_code varchar(64) not null comment 'category code',
    category_name varchar(128) not null comment 'category name',
    sort int default 0,
    status tinyint default 1,
    deleted tinyint default 0,
    create_time datetime default current_timestamp,
    update_time datetime default current_timestamp on update current_timestamp,
    primary key (id),
    unique key uk_base_product_category_code (category_code),
    index idx_base_product_category_parent_id (parent_id)
) engine=InnoDB default charset=utf8mb4;

-- Index for base_product.category_id (FK optional).
set @idx_exists := (
  select count(*)
  from information_schema.statistics
  where table_schema = database()
    and table_name = 'base_product'
    and index_name = 'idx_base_product_category_id'
);
set @sql := if(@idx_exists = 0,
  'create index idx_base_product_category_id on base_product(category_id)',
  'select 1'
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

-- Demo categories
insert into base_product_category (id, parent_id, category_code, category_name, sort, status, deleted)
values
    (1, 0, 'CAT-DEFAULT', '默认分类', 1, 1, 0),
    (2, 0, 'CAT-FOOD', '食品', 2, 1, 0),
    (3, 0, 'CAT-DAILY', '日用品', 3, 1, 0)
as new
on duplicate key update
    parent_id = new.parent_id,
    category_name = new.category_name,
    sort = new.sort,
    status = new.status,
    deleted = new.deleted;

-- System menu seed (category)
insert into sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible)
values
    (9104, 9100, '商品分类', '/base/category', 'views/BaseCategories.vue', 'base:category:view', 'Tickets', 'C', 4, 1)
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

insert into sys_role_menu (role_id, menu_id)
values
    (1, 9104),
    (2, 9104)
on duplicate key update role_id = role_id;

-- Button-level perms for categories (admin only).
insert into sys_menu (id, parent_id, menu_name, path, component, perms, icon, menu_type, sort, visible)
values
    (9141, 9104, '分类新增', null, null, 'base:category:add', null, 'F', 1, 0),
    (9142, 9104, '分类编辑', null, null, 'base:category:edit', null, 'F', 2, 0),
    (9143, 9104, '分类删除', null, null, 'base:category:remove', null, 'F', 3, 0)
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
    (1, 9141), (1, 9142), (1, 9143)
on duplicate key update role_id = role_id;

