-- Sales (Stage 5 UX): rename "Sales Orders" menu to record-only style.

update sys_menu
set menu_name = '销售订单（记录）'
where id = 9401;

