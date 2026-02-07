# 第五阶段：销售业务（Sales）实现记录与工程化拓展规划

本文用于记录 **OrderERP 第五阶段（销售业务）** 已落地实现内容，并给出更贴近企业真实场景的可扩展路线（按优先级），方便后续迭代上线。

> 约束与风格：延续前三/四阶段做法 —— Spring Boot 3 + Spring Data JPA + Spring Security(JWT) + Flyway；前端 Vue3 + Pinia + Element Plus；权限点用 `sys_menu(perms)` + `@PreAuthorize`；关键写路径尽量做到幂等/可回归；数据库迁移可重复执行（idempotent）。

---

## 1. 阶段目标（当前与未来）

### 1.1 P0 最小闭环（已落地）

实现“**下单 -> 审核锁库 -> 发货扣库**”闭环：

1) **创建销售订单**
- 选择客户（`base_partner.type=2`）、发货仓库（`base_warehouse`）
- 选择商品并填写数量（可选填写单价）
- 保存时写入 **商品快照**（`product_code/product_name/unit`），避免后续商品改名导致历史订单错乱

2) **审核（锁库）**
- 遍历订单明细，执行 `wms_stock.locked_qty += qty`
- 校验：`available = stock_qty - locked_qty >= qty`，否则报错“库存不足”

3) **发货（扣库）**
- 生成 `wms_io_bill`（`type=5`：销售出库）
- 执行扣库：`wms_stock.stock_qty -= qty` 且 `wms_stock.locked_qty -= qty`
- 写库存流水：`wms_stock_log.biz_type='SALES_OUT'`

> 口径约束：当前 WMS `wms_io_bill.type` 统一为  
> `1采购入库 / 2采购退货 / 3盘点入库 / 4盘点出库(含冲销) / 5销售出库`（由 V26 修正类型注释）。

### 1.2 P1 更贴近真实场景（已落地：分批发货/部分发货）

企业销售通常不会“一次性整单发完”，因此在 P0 基础上补齐：

- **分批发货 / 部分发货**
  - 同一销售订单可多次发货，生成多张“发货批次单”
  - 明细维护 `shipped_qty`（已发数量），订单状态可进入 `3部分发货`，直至全部发完进入 `4已发货`
- **作废订单释放锁库（增强）**
  - 草稿：直接作废
  - 已审核/部分发货：仅释放“未发部分”的锁库数量（`qty - shipped_qty`）

### 1.2.1 P1 已补齐：发货冲销（修正错误发货）

用于纠正“**误操作发货**”（货物实际上未出库/系统错误扣库）。冲销会：

- 回滚库存：`stock_qty += qty`
- 恢复锁库：`locked_qty += qty`（订单仍处于已审核可继续发货状态）
- 回滚订单已发数量：`sal_order_detail.shipped_qty -= qty`
- 生成一张冲销 WMS 单（`wms_io_bill.type=3`，`biz_id=原销售出库WMS单ID`）用于追溯与幂等
- 写库存流水：`wms_stock_log.biz_type='SALES_OUT_REVERSE'`

约束：
- 仅允许对“已发货批次（有 wmsBillId 且 WMS 单已完成）”执行冲销
- 幂等：重复冲销不会重复加库存，会返回同一张冲销 WMS 单

权限点：`sal:ship:reverse`（挂在“销售出库单”菜单下）

### 1.3 后续应实现（待拓展 / TODO）

销售真实场景通常还会出现：

- 拣货/复核/打包/出库（仓内作业）
- 应收对账/收款/开票（与第六阶段 Finance 联动）
- 价格权限隔离（销售单价可见/可编辑），客户信用额度/欠款控制
- 多仓发货、缺货拆单、预售/欠发、发货地址/物流信息、打印单据等

---

## 2. 数据模型与迁移（已落地）

### 2.1 Flyway

- `backend/src/main/resources/db/migration/V26__sales_p0_core.sql`
  - 修正 `wms_io_bill.type` 注释口径（不改变数据）
  - 建表：`sal_order`、`sal_order_detail`
  - 菜单与权限种子：销售管理 -> 销售订单（`sal:order:*`）

- `backend/src/main/resources/db/migration/V27__sales_p1_shipments.sql`
  - 增强订单状态注释：支持 `3部分发货`
  - 为 `sal_order_detail` 增加 `shipped_qty`
  - 建表：`sal_ship`、`sal_ship_detail`（发货批次）
  - 增加按钮权限：`sal:order:ship-batch`（分批发货）

- `backend/src/main/resources/db/migration/V32__sales_return.sql`
  - 新增销售退货表：`sal_return`、`sal_return_detail`
  - 新增菜单与权限：销售管理 -> 销售退货单（`sal:return:*`）
  - 补齐 WMS `wms_io_bill.type` 注释口径：新增 `6销售退货入库`

### 2.2 核心表说明

- `sal_order`：销售订单主表
  - `customer_*`：客户快照（代码/名称）
  - `warehouse_id`：发货仓库（P0 简化：整单单仓）
  - `status`：`1草稿 2已审核(锁库) 3部分发货 4已发货 9已作废`
  - `wms_bill_*`：最近一次关联的 WMS 销售出库单（便于快速追溯）

- `sal_order_detail`：销售订单明细
  - `product_* / unit`：商品快照
  - `qty`：订单数量
  - `shipped_qty`：已发数量（用于部分/分批发货）

- `sal_ship / sal_ship_detail`：发货批次单（每次发货生成一张）
  - `ship_no`：发货单号
  - `wms_bill_*`：关联的 WMS 销售出库单（`type=5`）

---

## 3. 接口与权限（已落地）

### 3.1 销售订单接口

Base：`/api/sales/orders`

- 列表：`GET /api/sales/orders`
- 详情：`GET /api/sales/orders/{id}`
- 新建：`POST /api/sales/orders`
- 一键生成并审核（锁库，不发货）：`POST /api/sales/orders/quick-audit`
- 一键闭环（生成订单→锁库→发货，失败回滚不落草稿）：`POST /api/sales/orders/quick-ship`
- 审核（锁库）：`POST /api/sales/orders/{id}/audit`
- 一键全量发货（发完剩余数量）：`POST /api/sales/orders/{id}/ship`
- 分批发货（按行填写本次发货数量）：`POST /api/sales/orders/{id}/ships`
  - body：`{ requestNo, lines: [{ orderDetailId, productId, qty }] }`（`requestNo` 用于幂等：前端重试不重复扣库/不重复生成发货批次）
- 作废（释放未发部分锁库）：`POST /api/sales/orders/{id}/cancel`
- 删除草稿（清理快速出库失败产生的草稿单）：`POST /api/sales/orders/{id}/delete`

### 3.2 发货批次查询接口

- 查询订单下发货批次：`GET /api/sales/orders/{id}/ships`
- 发货批次详情：`GET /api/sales/orders/ships/{shipId}`

### 3.3 销售出库记录接口（用于独立“记录栏”页面）

- 列表：`GET /api/sales/ships`
- 详情：`GET /api/sales/ships/{id}`
- 冲销（发货反冲/红冲）：`POST /api/sales/ships/{id}/reverse`

### 3.4 销售退货接口（客户退回入库）

Base：`/api/sales/returns`

- 列表：`GET /api/sales/returns`
- 详情：`GET /api/sales/returns/{id}`
- 新建：`POST /api/sales/returns`
  - body：`{ shipId, customerId, warehouseId, returnDate?, remark?, lines: [{ shipDetailId, qty }] }`
- 审核：`POST /api/sales/returns/{id}/audit`
- 收货入待检：`POST /api/sales/returns/{id}/receive`（进入 `wms_stock_qc`，库存不变）
- 质检通过并入库：`POST /api/sales/returns/{id}/execute`（`wms_stock_qc -> wms_stock`，生成 WMS 入库单 `type=6`）
- 质检不合格：`POST /api/sales/returns/{id}/qc-reject`（从 `wms_stock_qc` 扣减，不入库）
- 作废：`POST /api/sales/returns/{id}/cancel`

### 3.3 权限点（sys_menu.perms）

- 页面权限：`sal:order:view`
- 按钮权限：
  - `sal:order:add`
  - `sal:order:audit`
  - `sal:order:ship`
  - `sal:order:ship-batch`
  - `sal:order:cancel`
  - `sal:order:delete`

- 销售出库单页面权限：`sal:ship:view`
- 销售出库单按钮权限：`sal:ship:detail`
- 销售出库单冲销权限：`sal:ship:reverse`

- 销售退货单页面权限：`sal:return:view`
- 销售退货单按钮权限：
  - `sal:return:add`
  - `sal:return:audit`
  - `sal:return:receive`
  - `sal:return:execute`
  - `sal:return:qc-reject`
  - `sal:return:cancel`

---

## 4. 前端 UI/交互（已落地）

菜单：`销售管理 -> 销售订单`

- 作为**记录页**：只用于查看销售订单与明细（含：数量/已发/未发、关联发货批次）
- 详情页：
  - 明细展示 `数量 / 已发 / 未发`
  - 展示发货批次列表（发货单号、数量合计、WMS 单号等）

菜单：`销售管理 -> 销售出库单（发货记录）`

- 列表查看所有发货批次（支持按客户/仓库/日期/关键字筛选）
- 详情查看发货明细（本次出库的商品与数量）
- 新建销售出库：
  - “新建销售出库（生成订单→锁库→发货）”：用于快速完成闭环
  - “新建销售订单(锁库)”：用于“先锁库、后分批发货”的场景（成功后可在“新增发货批次”里选择该订单）
  - “新增发货批次（按销售订单）”：对已审核/部分发货的订单继续发货

菜单：`销售管理 -> 销售退货单`

- 新建销售退货：先选择“发货批次（shipNo）”，系统带出客户/仓库并展示明细「本批次发货 / 已退 / 可退」，只填写本次退货数量
- 单价/金额：由来源销售订单行价格计算（前端不再允许手工填单价）；无价格权限时字段保留但显示为 `-`
- 审核：确认退货单
- 收货入待检：数量进入 `wms_stock_qc`（库存不变）
- 质检通过：`wms_stock_qc -> wms_stock`，增加库存并生成 WMS 入库单（`type=6`）
- 不合格：从 `wms_stock_qc` 扣减并记录处置方式（退回客户/报废/返修）

菜单：`销售管理 -> 销售对账单`

- 列表：按客户/对账周期/关键字筛选，支持新建对账单
- 详情：展示对账单据（发货批次/退货单）及商品摘要；支持审核、草稿重生成、作废
- 审核后：支持登记收款/登记发票；收款累计驱动状态（已审核/部分已收/已结清）

---

## 5. 关键约束与幂等性（已落地）

- 审核锁库：必须校验 `available >= qty`
- 发货扣库：必须同时扣减 `stock_qty` 与释放 `locked_qty`，并写 `SALES_OUT` 流水
- 分批发货：
  - 每次发货生成 `sal_ship`，并用 `wms_io_bill(biz_id=shipId, type=5)` 做幂等兜底（避免并发重复生成 WMS 单）
  - `sal_order_detail.shipped_qty` 严格不允许超过 `qty`
  - 分批发货接口幂等：`sal_ship.request_no` 唯一（同一 `requestNo` 重试直接返回，不重复扣库/不重复生成批次）
- 作废：
  - 已审核/部分发货仅释放“未发部分”的锁库，已发货禁止作废（后续由“发货冲销/退货”处理）

---

## 6. 待拓展清单（建议优先级）

### P1（业务补齐）

- 销售退货（客户退回入库）：已落地（`wms_io_bill.type=6` + `wms_stock_log.biz_type='SALES_RETURN'`）
- 销售退货关联原发货批次：已落地
  - Flyway：`backend/src/main/resources/db/migration/V35__sales_return_link_ship.sql`
  - 数据：`sal_return` 增加 `ship_id/ship_no/order_id/order_no`；`sal_return_detail` 增加 `ship_detail_id/order_detail_id`，并将唯一约束调整为 `(return_id, ship_detail_id)`
  - 约束：按 `sal_ship_detail` 校验“可退数量”，并在发货批次详情返回 `returnedQty/returnableQty` 供前端填写
- 销售退货质检与处置：已落地
  - Flyway：`backend/src/main/resources/db/migration/V37__sales_return_qc.sql`
  - 流程：审核 -> 收货入待检（`wms_stock_qc`）-> 质检通过入库 / 质检不合格处置
- TODO：退货原因/责任方、二次入库策略

### P2（工程化/财务联动）

- 应收对账单（AR Bill）：已落地（生成/审核/单据锁定防重复对账/收款登记/发票登记/作废/草稿重生成）
  - Flyway：`backend/src/main/resources/db/migration/V33__sales_ar_bill.sql`
  - 后端：`/api/sales/ar-bills`（权限：`sal:ar:view/add/audit/recv/invoice/cancel/regen`）
  - 前端：`frontend/src/views/SalesArBills.vue`（菜单：销售管理 -> 销售对账单）
  - 自动化测试（Testcontainers）：`backend/src/test/java/com/ordererp/backend/sales/SalesStage5ArBillIT.java`
- 销售单价权限隔离：已落地（可见/可编辑，接口脱敏 + 前端显隐）
  - Flyway：`backend/src/main/resources/db/migration/V34__sales_p2_price_perms.sql`（权限：`sal:price:view`、`sal:price:edit`）
  - 后端脱敏：
    - 无 `sal:price:view/edit`：销售订单/销售退货接口返回中 `totalAmount/price/amount` 置空
    - 新增/创建：销售订单 `sal:order:add` 额外要求 `sal:price:edit`；销售退货单价由来源订单带出，不再要求 `sal:price:edit`
  - 前端显隐：`frontend/src/views/SalesOrders.vue`、`frontend/src/views/SalesReturns.vue`
- 客户信用额度/欠款控制：已落地（超额禁止“审核销售订单”，避免继续锁库/发货）
  - 启用条件：`base_partner.credit_limit > 0`（<=0 视为不启用额度控制，方便默认数据不阻塞流程）
  - 口径（更贴近企业场景的“额度占用”）：
    - 已对账 AR 未收：`sum(max(total_amount - received_amount, 0))`（`sal_ar_bill.status in 2/3/4`）
    - 未对账发货/退货净额：已发货未被 AR 引用（`sal_ship` left join `sal_ar_doc_ref`） + 已执行退货未被引用（金额为负）
    - 未发完订单占用：已审核/部分发货订单剩余金额（`sal_order.status in 2/3`，按 `price * (qty - shipped_qty)` 汇总）
  - 拦截点：`SalOrderService.audit(...)` 审核前调用 `SalCreditService.checkIncrease(...)`
  - UI 展示（让数据更直观）：
    - 销售管理 -> 销售出库单 -> 新建销售出库：选客户后展示「信用额度 / 已占用 / 可用」
    - 基础资料 -> 往来单位：客户列表增加「已占用额度 / 可用额度」两列（按当前页实时计算）
  - 回归测试（Testcontainers）：`backend/src/test/java/com/ordererp/backend/sales/SalesStage5CreditLimitIT.java`

### P3（仓内作业）

- 拣货单/复核单/装箱单/面单打印
- 多仓发货、缺货拆单、预售欠发等复杂履约

---

## 7. 测试建议（闭环验证）

### 7.1 手工验收（前端）

销售退货闭环（客户退回入库）：

1) 基础资料准备：至少有 1 个客户、1 个商品、1 个仓库
2) 菜单：`销售管理 -> 销售退货单`
3) 新建退货单：选择“发货批次（shipNo）”，系统带出客户/仓库；按明细行填写本次退货数量（不超过“可退”）
4) 审核：状态应从 `待审核(1)` -> `待收货(2)`
5) 收货入待检：状态应变为 `待质检(3)`，库存不变（进入 `wms_stock_qc`）
6) 质检通过：状态应变为 `已完成(4)`，并生成 `WMS单号`；同时仓库库存增加
7) 验证库存流水：`库存管理 -> 库存流水` 中可查询到 `销售退货入库（SALES_RETURN）` 记录（用 WMS 单号搜索最直观）

### 7.2 自动化回归（后端集成测试）

- 测试类：`backend/src/test/java/com/ordererp/backend/sales/SalesStage5ReturnIT.java`
- 覆盖：创建/审核/执行/幂等（重复执行不重复加库存、不重复写流水）
- 运行：`cd backend && mvn test -Dtest=SalesStage5ReturnIT`

- 测试类：`backend/src/test/java/com/ordererp/backend/sales/SalesStage5ArBillIT.java`
- 覆盖：销售出库/退货 -> AR 对账单生成/审核 -> 收款登记/发票登记 -> 单据锁定防重复对账 -> 禁止带收款/开票作废
- 运行：`cd backend && mvn test -Dtest=SalesStage5ArBillIT`

> 说明：该项目测试使用 Testcontainers，需要本机 Docker Desktop 正常运行。
