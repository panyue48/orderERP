-- Demo seed script (manual).
--
-- Purpose:
-- - Provide a safe way to seed demo data in DEV without coupling it to Flyway production migrations.
--
-- Usage:
-- - Run against your dev schema (e.g. `erp_data`) in MySQL client.
-- - Prefer running on an empty/dev database only.

-- WMS (Stage 3): rename to inventory check-in/out and seed demo data
-- Goal:
-- - align type=3/4 with erp_data.sql semantics (盘点入库/盘点出库)
-- - create some demo warehouses/products/stocks/bills so dropdowns and lists have data

-- Rename menu labels (keep paths/perms)
update sys_menu set menu_name = '盘点入库' where id = 9202 and menu_name <> '盘点入库';
update sys_menu set menu_name = '盘点出库' where id = 9203 and menu_name <> '盘点出库';

-- Demo base data (safe: new codes, high ids)
insert into base_warehouse (id, warehouse_code, warehouse_name, status, deleted)
values
    (5001, 'DEMO-WH-02', '备用仓库', 1, 0),
    (5002, 'DEMO-WH-03', '退货仓', 1, 0)
as new
on duplicate key update
    warehouse_code = new.warehouse_code,
    warehouse_name = new.warehouse_name,
    status = new.status,
    deleted = new.deleted;

insert into base_product (id, product_code, product_name, unit, purchase_price, sale_price, status, deleted)
values
    (50001, 'DEMO-SKU-101', '测试商品101', '个', 12.00, 18.00, 1, 0),
    (50002, 'DEMO-SKU-102', '测试商品102', '个', 5.50, 9.90, 1, 0),
    (50003, 'DEMO-SKU-103', '测试商品103', '个', 20.00, 29.90, 1, 0)
as new
on duplicate key update
    product_code = new.product_code,
    product_name = new.product_name,
    unit = new.unit,
    purchase_price = new.purchase_price,
    sale_price = new.sale_price,
    status = new.status,
    deleted = new.deleted;

-- Demo stocks (only upsert when missing; do not override existing)
insert into wms_stock (warehouse_id, product_id, stock_qty, locked_qty, version)
values
    (5001, 50001, 30.000, 0.000, 0),
    (5001, 50002, 8.000, 0.000, 0),
    (5002, 50002, 15.000, 0.000, 0),
    (5002, 50003, 6.000, 0.000, 0)
as new
on duplicate key update
    warehouse_id = wms_stock.warehouse_id;

-- Demo pending bills (so pages can list & execute)
insert into wms_io_bill (id, bill_no, type, biz_id, biz_no, warehouse_id, status, remark, create_by, create_time)
values
    (99001, 'SI-DEMO-001', 3, null, null, 5001, 1, '示例盘点入库单（可直接执行）', 'admin', current_timestamp),
    (99002, 'SO-DEMO-001', 4, null, null, 5001, 1, '示例盘点出库单（可直接执行）', 'admin', current_timestamp)
as new
on duplicate key update
    warehouse_id = new.warehouse_id,
    status = new.status,
    remark = new.remark;

insert ignore into wms_io_bill_detail (id, bill_id, product_id, qty, real_qty)
values
    (990011, 99001, 50003, 2.000, 0.000),
    (990021, 99002, 50001, 1.000, 0.000);

-- Demo completed bill + stock log to demonstrate "库存流水"
insert into wms_io_bill (id, bill_no, type, biz_id, biz_no, warehouse_id, status, remark, create_by, create_time)
values
    (99003, 'SI-DEMO-000', 3, null, null, 5002, 2, '示例已完成盘点入库（用于展示流水）', 'admin', current_timestamp)
as new
on duplicate key update
    warehouse_id = new.warehouse_id,
    status = new.status,
    remark = new.remark;

insert ignore into wms_io_bill_detail (id, bill_id, product_id, qty, real_qty)
values
    (990031, 99003, 50002, 3.000, 3.000);

-- Ensure stock row exists and apply the completed stock-in effect once
insert into wms_stock (warehouse_id, product_id, stock_qty, locked_qty, version)
values (5002, 50002, 0.000, 0.000, 0)
as new
on duplicate key update
    warehouse_id = wms_stock.warehouse_id;

update wms_stock set stock_qty = stock_qty + 3.000 where warehouse_id = 5002 and product_id = 50002;

insert ignore into wms_stock_log (id, warehouse_id, product_id, biz_type, biz_no, change_qty, after_stock_qty, create_time)
values
    (990032, 5002, 50002, 'STOCK_IN', 'SI-DEMO-000', 3.000,
     (select stock_qty from wms_stock where warehouse_id = 5002 and product_id = 50002),
     current_timestamp);

