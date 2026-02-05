-- Purchase UX tweaks: make menu names reflect user mental model.
-- Idempotent updates.

update sys_menu
set menu_name = '采购订单(记录)'
where id = 9301;

