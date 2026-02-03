-- WMS Core extension: physical stock check bills (counted_qty based).
-- Stage-3 hardening plan 6.4: upgrade from "stock in/out delta" to "counted stock" model.

create table if not exists wms_check_bill (
    id bigint not null auto_increment,
    bill_no varchar(64) not null comment 'check bill no',
    warehouse_id bigint not null comment 'warehouse id',
    status tinyint default 1 comment '1 pending, 2 completed',
    remark varchar(255) default null,
    create_by varchar(64) default null,
    create_time datetime default current_timestamp,
    execute_time datetime default null,
    primary key (id),
    unique key uk_wms_check_bill_no (bill_no),
    key idx_wms_check_bill_warehouse (warehouse_id)
) engine=InnoDB default charset=utf8mb4 comment='wms physical stock check bill';

create table if not exists wms_check_bill_detail (
    id bigint not null auto_increment,
    bill_id bigint not null,
    product_id bigint not null,
    counted_qty decimal(16, 3) not null comment 'counted qty',
    book_qty decimal(16, 3) default 0.000 comment 'book qty at execute time',
    diff_qty decimal(16, 3) default 0.000 comment 'diff = counted - book',
    primary key (id),
    unique key uk_wms_check_bill_detail_bill_prod (bill_id, product_id),
    key idx_wms_check_bill_detail_bill (bill_id)
) engine=InnoDB default charset=utf8mb4 comment='wms physical stock check bill detail';
