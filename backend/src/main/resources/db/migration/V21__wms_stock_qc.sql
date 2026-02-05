-- Stage 4 (Purchase) extension: QC stock bucket
-- wms_stock_qc keeps "received but not yet QC-passed" quantity, separated from physical stock.

create table if not exists wms_stock_qc (
    id bigint not null auto_increment,
    warehouse_id bigint not null comment 'warehouse id',
    product_id bigint not null comment 'product id',
    qc_qty decimal(16, 3) not null default 0.000 comment 'qty pending QC (not available)',
    version int default 0 comment 'optimistic lock version',
    update_time datetime default current_timestamp on update current_timestamp,
    primary key (id),
    unique key uk_wms_stock_qc_wh_prod (warehouse_id, product_id),
    key idx_wms_stock_qc_wh_prod (warehouse_id, product_id)
) engine=InnoDB default charset=utf8mb4 comment='wms stock qc bucket';

-- Backfill: aggregate existing pending-QC purchase inbounds into QC bucket.
insert into wms_stock_qc (warehouse_id, product_id, qc_qty, version, update_time)
select
  t.warehouse_id,
  t.product_id,
  t.qc_qty,
  0 as version,
  now() as update_time
from (
  select
    i.warehouse_id as warehouse_id,
    d.product_id as product_id,
    coalesce(sum(d.plan_qty), 0.000) as qc_qty
  from pur_inbound i
  join pur_inbound_detail d on d.inbound_id = i.id
  where i.status = 1
    and i.qc_status = 1
  group by i.warehouse_id, d.product_id
) t
on duplicate key update
  qc_qty = values(qc_qty),
  update_time = values(update_time);

-- Ensure wms_stock rows exist so stock query can show qc_qty even when stock_qty is 0.
insert into wms_stock (warehouse_id, product_id, stock_qty, locked_qty, version, update_time)
select
  q.warehouse_id,
  q.product_id,
  0.000 as stock_qty,
  0.000 as locked_qty,
  0 as version,
  now() as update_time
from wms_stock_qc q
left join wms_stock s on s.warehouse_id = q.warehouse_id and s.product_id = q.product_id
where s.id is null;

