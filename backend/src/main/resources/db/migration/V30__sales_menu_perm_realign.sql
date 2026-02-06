-- Sales (Stage 5 UX): realign button perms with user mental model.
-- - "销售订单（记录）" should be record-only.
-- - Operational buttons (add/audit/ship/cancel/ship-batch) are used from "销售出库单" page.
-- Safe for already-initialized databases (idempotent updates).

-- Move existing button-level perms under "销售出库单" (menu_id=9402) while keeping perms strings unchanged.
update sys_menu
set parent_id = 9402, menu_name = '销售出库新增'
where id = 9411;

update sys_menu
set parent_id = 9402, menu_name = '销售出库审核(锁库)'
where id = 9412;

update sys_menu
set parent_id = 9402, menu_name = '销售出库发货'
where id = 9413;

update sys_menu
set parent_id = 9402, menu_name = '销售出库作废'
where id = 9414;

update sys_menu
set parent_id = 9402, menu_name = '销售出库分批发货'
where id = 9416;

