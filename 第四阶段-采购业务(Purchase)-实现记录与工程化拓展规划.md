# 第四阶段：采购业务（Purchase）实现记录与工程化拓展规划

本文用于记录 **OrderERP 第四阶段（采购业务）** 已落地实现内容，并给出更贴近企业真实场景的可扩展路线（按优先级），方便后续迭代上线。

> 约束与风格：延续前三阶段做法 —— Spring Boot 3 + Spring Data JPA + Spring Security(JWT) + Flyway；前端 Vue3 + Pinia + Element Plus；权限点用 `sys_menu(perms)` + `@PreAuthorize`；关键写路径尽量做到幂等/可回归；数据库迁移可重复执行（idempotent）。

---

## 1. 阶段目标（当前与未来）

### 1.1 当前已实现（最小闭环）

实现“**采购单 -> 审核 -> 入库 -> 加库存**”的最小可用闭环：

- 采购单：创建并保存主表+明细（含商品快照字段）
- 审核：状态流转（待审核 -> 已审核）
- 入库：选择仓库后“一键全量入库”
  - 生成 WMS 入库单（`wms_io_bill.type=1`）
  - 更新库存（`wms_stock.stock_qty += qty`）
  - 写入库存流水（`wms_stock_log.biz_type='PURCHASE_IN'`）
  - 回写采购单为已完成（`pur_order.status=4`）
- 入库幂等：同一采购单只会生成一张采购入库单；重复入库不会重复加库存/重复写流水

**P0 扩展已落地：分批入库 / 部分入库（更贴近真实场景）**

- 新增“采购入库单（收货单）”对象：`pur_inbound / pur_inbound_detail`
- 同一采购单允许多次入库，采购单状态可进入 `3部分入库`，直至全部入完进入 `4已完成`
- 写路径幂等：分批入库由客户端提供 `requestNo(UUID)`，后端基于 `pur_inbound.request_no` 唯一约束实现“重复提交不重复加库存”

### 1.2 企业真实场景（未来应实现）

企业真实采购通常不是“一键全量入库”，而是会出现：

- 多次到货、分批入库（部分入库）
- 少到/多到、入库差异、补单/退货
- 质检（IQC）与不合格处理
- 采购对账/发票/付款（与财务模块联动）

---

## 2. 数据模型与迁移（已落地）

### 2.1 Flyway

- 迁移文件：`backend/src/main/resources/db/migration/V15__purchase_core.sql`
- 内容：
  - 建表：`pur_order`、`pur_order_detail`
  - 菜单与权限种子：采购管理 -> 采购订单

- 迁移文件：`backend/src/main/resources/db/migration/V16__purchase_inbound.sql`
- 内容：
  - 建表：`pur_inbound`、`pur_inbound_detail`
  - 菜单与权限种子：采购管理 -> 采购入库单

- 迁移文件：`backend/src/main/resources/db/migration/V20__purchase_inbound_iqc.sql`
- 内容：
  - 为 `pur_inbound` 增加质检字段（`qc_status/qc_by/qc_time/qc_remark`）
  - 增加入库质检权限点：`pur:inbound:iqc`

- 迁移文件：`backend/src/main/resources/db/migration/V17__purchase_return.sql`
- 内容：
  - 建表：`pur_return`、`pur_return_detail`
  - 菜单与权限种子：采购管理 -> 采购退货单

### 2.2 表结构（与 erp_data.sql 对齐）

- `pur_order`
  - 关键字段：`order_no`、`supplier_id`、`order_date`、`total_amount`、`pay_amount`、`status`、`audit_by/audit_time`
  - 状态含义（参考 `erp_data.sql` 语义）：`1待审核 2已审核 3部分入库 4已完成 9已作废`
- `pur_order_detail`
  - 商品快照字段：`product_code/product_name/unit`（避免商品信息变更影响历史单据）
  - 金额字段：`price/qty/amount`（金额计算必须用 BigDecimal）
  - 进度字段：`in_qty`（本阶段入库后会直接写成等于 qty；为后续“分批入库”预留）

---

## 3. 后端实现（已落地）

### 3.1 模块位置

- `backend/src/main/java/com/ordererp/backend/purchase`
  - `entity/`：`PurOrder*`、`PurInbound*`、`PurReturn*`
  - `repository/`：`PurOrder*Repository`、`PurInbound*Repository`、`PurReturn*Repository`
  - `service/`：`PurOrderService`、`PurInboundService`、`PurReturnService`
  - `controller/`：`PurOrderController`、`PurInboundController`、`PurReturnController`

### 3.2 接口清单

- 列表：`GET /api/purchase/orders`
- 详情：`GET /api/purchase/orders/{id}`
- 新建：`POST /api/purchase/orders`
- 审核：`POST /api/purchase/orders/{id}/audit`
- 入库：`POST /api/purchase/orders/{id}/inbound`
  - 请求：`{ "warehouseId": 123 }`
  - 说明：本阶段为“一键全量入库”
- 分批入库：`POST /api/purchase/orders/{id}/inbounds`
  - 请求：`{ "requestNo": "uuid", "warehouseId": 123, "remark": "...", "lines": [{ "productId": 1, "qty": 1.000 }] }`
  - 说明：支持分批入库；qty 不能超过剩余可入数量；重复提交相同 requestNo 不会重复加库存
- 作废：`POST /api/purchase/orders/{id}/cancel`

### 3.2.1 采购入库单接口

- 列表：`GET /api/purchase/inbounds`（支持按采购单过滤：`?orderId=123`）
- 详情：`GET /api/purchase/inbounds/{id}`
- 新建：`POST /api/purchase/inbounds`（新建采购入库，同时自动生成采购订单；默认进入“待质检”，不直接加库存）
- 质检通过并入库：`POST /api/purchase/inbounds/{id}/iqc-pass`
- 质检不合格：`POST /api/purchase/inbounds/{id}/iqc-reject`

质检交互辅助接口（用于“新增收货批次”的选择与提示）：

- 采购单下拉：`GET /api/purchase/orders/options`（仅返回可继续收货的采购单）
- 待检汇总：`GET /api/purchase/orders/{id}/pending-qc-summary`（返回待检批次数量 + 待检数量汇总）

### 3.2.2 采购退货单接口（P1 已落地）

- 列表：`GET /api/purchase/returns`
- 详情：`GET /api/purchase/returns/{id}`
- 新建：`POST /api/purchase/returns`
- 审核：`POST /api/purchase/returns/{id}/audit`
- 执行：`POST /api/purchase/returns/{id}/execute`（生成 WMS 出库单并扣减库存）
- 作废：`POST /api/purchase/returns/{id}/cancel`

### 3.2.3 采购对账单接口（P1 已落地）

对账对象：同一供应商在周期内的“已完成入库（质检通过）”与“已完成退货”，用于形成应付结算闭环。

- 列表：`GET /api/purchase/ap-bills`
- 详情：`GET /api/purchase/ap-bills/{id}`（包含：对账单据汇总 + 付款记录 + 发票记录）
- 新建：`POST /api/purchase/ap-bills`
  - 请求：`{ "supplierId": 1, "startDate": "2026-02-01", "endDate": "2026-02-29", "remark": "..." }`
  - 说明：创建时自动汇总入库/退货并“占用单据”，避免重复进入其他对账单
- 审核：`POST /api/purchase/ap-bills/{id}/audit`
- 重新生成：`POST /api/purchase/ap-bills/{id}/regenerate`（仅草稿允许；审核后单据锁定）
- 作废：`POST /api/purchase/ap-bills/{id}/cancel`（要求：未发生付款/开票）
- 付款登记：`POST /api/purchase/ap-bills/{id}/payments`
- 付款作废：`POST /api/purchase/ap-bills/{id}/payments/{paymentId}/cancel`
- 发票登记：`POST /api/purchase/ap-bills/{id}/invoices`
- 发票作废：`POST /api/purchase/ap-bills/{id}/invoices/{invoiceId}/cancel`

### 3.3 权限点（sys_menu.perms）

在 `V15__purchase_core.sql` 中落地：

- `pur:order:view`：查看
- `pur:order:add`：新建
- `pur:order:audit`：审核
- `pur:order:inbound`：入库
- `pur:order:cancel`：作废

授权策略（当前实现）：

- admin（role_id=1）：全量权限
- sale_mgr（role_id=2）：只读

补充（已落地）：
- 采购入库单：`pur:inbound:view`
- 采购入库质检：`pur:inbound:iqc`
- 采购退货单：`pur:return:view/add/audit/execute/cancel`

### 3.4 与 WMS 的配合（关键点）

采购入库（质检通过后）会写入 WMS 的通用出入库表：

- `wms_io_bill.type=1`：采购入库（Purchase In）
- `wms_io_bill.biz_no`：用于追溯关联（采购单号/入库单号）
- `wms_stock_log.biz_type='PURCHASE_IN'`：库存流水标识

幂等策略（当前实现）：

- 创建“收货批次单（待质检）”时：以 `pur_inbound.request_no` 做幂等键（同 requestNo 重复提交返回同一张批次单，不会重复累加待检库存）
- 质检通过执行入库时：以 `pur_inbound.wms_bill_id/wms_bill_no` 判断是否已执行（已执行则幂等返回，不重复加库存/写流水）

> 说明：`wms_io_bill` 在第三阶段引入了全表唯一索引 `unique(biz_id, type)`（用于冲销单幂等）。
> 为避免不同业务表的 ID 在 biz_id 上发生碰撞，采购域不使用 `biz_id` 作为外部幂等键，改用 `biz_no`/采购域自身的 requestNo。

---

## 4. 前端实现（已落地）

### 4.1 菜单路由

- 菜单：`采购管理 -> 采购订单 / 采购入库单 / 采购退货单`
- 组件映射：
  - 后端 sys_menu.component：`views/PurchaseOrders.vue`
  - 后端 sys_menu.component：`views/PurchaseInbounds.vue`
  - 后端 sys_menu.component：`views/PurchaseReturns.vue`
  - 前端路由映射：`frontend/src/router/dynamic.ts`

### 4.2 页面

- 页面文件：`frontend/src/views/PurchaseOrders.vue`
- 支持：
  - 列表/搜索/分页
  - 详情查看（含明细）
  - 说明：该页面用于**查看采购订单记录**；具体入库操作在“采购入库单”中完成，退货操作在“采购退货单”中完成（详情弹窗提供“新增收货批次”跳转入口）

### 4.2.1 采购入库单页面

- 页面文件：`frontend/src/views/PurchaseInbounds.vue`
- 支持：列表/搜索/分页、详情查看（含明细）
- 支持（P0 工程化）：
  - 新建采购入库（自动生成采购订单）
  - 新增收货批次（选择采购单 → 填本次到货数量 → 生成新的“待质检”批次）
  - 关键约束：若同一采购单已存在待质检批次，新增时会弹窗提示待检批次数量与待检数量汇总，避免“越点越多”的误解

### 4.2.2 采购退货单页面（P1 已落地）

- 页面文件：`frontend/src/views/PurchaseReturns.vue`
- 支持：列表/搜索/分页、新建/审核/执行/作废、详情查看（含明细）

### 4.2.3 采购对账单页面（P1 已落地）

- 页面文件：`frontend/src/views/PurchaseApBills.vue`
- 支持：列表/搜索/分页、详情查看（含对账单据/付款/发票）
- 支持：新建对账单、审核、登记付款/发票、作废（未发生付款/开票时）

### 4.3 前端 API

- `frontend/src/api/purchase.ts`：采购订单 API
- 供应商选项：复用基础资料接口 `/api/base/partners/options`，前端过滤 `type===1`
- 商品/仓库选项：复用 WMS 已有 options 接口

---

## 5. 自动化测试（已落地）

集成测试（Testcontainers + MySQL）：

- `backend/src/test/java/com/ordererp/backend/purchase/PurchaseStage4IT.java`
  - 采购单创建金额计算
  - 审核状态流转
  - 入库生成 WMS 单 + 加库存 + 写流水
  - 入库幂等（重复入库不重复加库存/不重复写流水）
- `backend/src/test/java/com/ordererp/backend/purchase/PurchaseStage4PartialInboundIT.java`：分批入库（2->3->4）
- `backend/src/test/java/com/ordererp/backend/purchase/PurchaseStage4ReturnIT.java`：采购退货（扣库存 + PURCHASE_RETURN 流水 + 幂等）

---

## 6. 已知限制（当前版本的“刻意简化”）

1) **入库已支持“分批入库”，但仍偏简化**

- 当前已支持：分批到货、部分入库、订单进度回写
- 仍缺少：收货差异（少到/多到）、补单、入库冲销/红冲等更贴近真实现场的异常处理闭环

2) **采购模块仍偏轻量**

- 当前已具备：采购订单/采购入库单（分批收货+质检门禁）/采购退货单/采购对账-付款-发票（应付闭环）
- 仍缺少：围绕采购的更多业务对象与异常处理（如：收货差异、补单、入库冲销/红冲、供应商索赔/补发等）

---

## 7. 未来待拓展（按优先级，建议迭代顺序）

### P0（已落地）：分批入库 / 部分入库（更贴近真实场景）

- 已落库：`pur_inbound / pur_inbound_detail`
- 入库执行时生成 WMS 单：`wms_io_bill.type=1`，并用 `biz_no=pur_inbound.inbound_no` 做追溯关联
- 幂等：`requestNo(UUID)` + `pur_inbound.request_no` 唯一约束
- 回写采购单进度：
  - 明细层面：`pur_order_detail.in_qty += inbound_qty`
  - 主表状态：`status=3(部分入库)` / `status=4(已完成)`

### P0（已落地）：采购入库冲销/红冲（修正错误入库）

目标：入库错误时可回滚库存与流水，且行为可审计。

- 已实现做法（当前版本）：

- 在采购域内提供“入库批次冲销”接口：`POST /api/purchase/inbounds/{id}/reverse`（权限：`pur:inbound:reverse`）
- 仅允许冲销“已完成（质检通过已入库）”的批次单
- 幂等：通过 WMS 表 `unique(biz_id, type)` 保证同一张入库单只生成 1 张冲销单
- 冲销会生成一张 WMS 出库单（`wms_io_bill.type=4`，`biz_id=原采购入库WMS单ID`），并：
  - `wms_stock.stock_qty -= 本批次数量`
  - 写入库存流水：`wms_stock_log.biz_type='PURCHASE_IN_REVERSE'`
  - 回滚采购入库进度：`pur_order_detail.in_qty -= 本批次数量`，并重算 `pur_order.status`
- 关键约束：若该批次已进入对账单（`pur_ap_doc_ref` 占用），禁止冲销，避免财务闭环被破坏

### P1（已落地）：采购退货（退供应商）

- 已落库：`pur_return / pur_return_detail`
- 执行时生成 WMS 出库单：`wms_io_bill.type=2`（采购退货出库），`biz_no=pur_return.return_no` 做追溯
- 库存流水：`wms_stock_log.biz_type='PURCHASE_RETURN'`（change_qty 为负）
- 幂等：退货单加悲观锁；已完成重复执行直接返回已关联的 WMS 单，不重复扣库存

### P1：质检（IQC）与入库质检状态

### P1（已落地）：质检（IQC）与入库质检状态

#### 设计目标（为什么要这么做）

- 贴近真实仓库：收货后通常先进入“待检区”，**未质检通过前不应计入可用库存**
- 贴近用户心智：用户点击“新增收货批次”后，看到“待质检”数量增加，理解“货到了但库存没变”的原因
- 避免误操作：同一采购单存在待检批次时，新增批次要有明确提示，避免“越点越多”的错觉

#### 数据模型（落库点）

- `pur_inbound` 增加 IQC 字段（Flyway：`V20__purchase_inbound_iqc.sql`）
  - `qc_status/qc_by/qc_time/qc_remark`
  - 入库单状态：`status=1 待质检 / 2 已完成 / 9 已作废`
- 新增待检库存桶 `wms_stock_qc`（Flyway：`V21__wms_stock_qc.sql`）
  - `qc_qty`：待质检数量（不参与可用库存计算）
  - 用途：在“质检通过”前承接到货数量，质检通过后再转入 `wms_stock.stock_qty`
  - 兼容历史：迁移脚本会把已有“待质检”的收货批次汇总回填到 `wms_stock_qc.qc_qty`

#### 核心流程（后端事务内做什么）

1) 新增收货批次 / 新建采购入库（都会生成一张 `pur_inbound`，状态=待质检）
   - 写入 `pur_inbound/pur_inbound_detail`
   - **不生成** `wms_io_bill`
   - **不增加** `wms_stock.stock_qty`
   - **增加** `wms_stock_qc.qc_qty += 本次到货量`

2) 质检通过并入库：`POST /api/purchase/inbounds/{id}/iqc-pass`
   - 将 `pur_inbound.qc_status=通过`，记录 `qc_by/qc_time/qc_remark`
   - `wms_stock_qc.qc_qty -= 本批次数量`（待检 -> 可用）
   - 生成 `wms_io_bill.type=1`（采购入库，`biz_no=入库单号`）+ 明细
   - `wms_stock.stock_qty += 本批次数量`
   - 写库存流水：`wms_stock_log.biz_type='PURCHASE_IN'`
   - 回写：`pur_inbound.status=2 + wms_bill_no + execute_by/time`
   - 回写采购单进度：采购明细 `in_qty += 本批次数量`，并将 `pur_order.status` 更新为 `部分入库/已完成`

3) 质检不合格：`POST /api/purchase/inbounds/{id}/iqc-reject`
   - 将 `pur_inbound.qc_status=不合格`，并作废 `pur_inbound.status=9`
   - `wms_stock_qc.qc_qty -= 本批次数量`（不进入可用库存）
   - 不生成 WMS 单、不写入 `PURCHASE_IN` 流水
   - 退供应商的业务（出库/账务）仍应走“采购退货单”闭环

#### 前端交互（采购入库单页的用户路径）

- 入库单列表行内操作仅保留：`详情 / 质检通过 / 不合格`（都针对“这张批次单”）
- “新增收货批次（继续入库）”入口移动到页面顶部按钮：先选 PO → 填本次到货数量 → 生成新的待检批次
- 关键约束（防误解）：若同一 PO 已存在待检批次，提交新增时会弹窗提示：
  - “当前已有 X 张待质检批次单，是否继续新增到货批次？”
  - 并列出待检数量汇总（按商品汇总），让用户知道库存没变的原因与当前待检规模
- PO 页面只读，但在 PO 详情弹窗提供“新增收货批次”的跳转入口（带出该 PO 并自动打开新增弹窗）

#### 验收要点（你可以怎么观察它是否正确）

- 新增收货批次后：`库存查询` 的 `待质检(qcQty)` 增加，`物理库存/可用库存` 不变
- 点击“质检通过”后：`qcQty` 扣减，`物理库存/可用库存` 增加，并出现 `采购入库` 流水
- 点击“不合格”后：`qcQty` 扣减，`物理库存/可用库存` 不变，批次单作废

### P1：采购对账/发票/付款（与财务模块联动）

### P1（已落地）：采购对账/发票/付款（应付闭环）

目标：采购完成后进入“对账 -> 付款 -> 已付/部分付”的资金闭环（本阶段先实现采购域内的应付闭环，后续可对接第六阶段资金账户）。

- 对账单：`pur_ap_bill / pur_ap_bill_detail`
  - 创建时按供应商+周期汇总“已完成入库（质检通过）”与“已完成退货”单据
  - 使用 `pur_ap_doc_ref` 做占用，避免同一入库/退货单据进入多个对账单
  - 锁定规则：对账单审核后单据列表锁定，不允许再新增/变更单据；若需刷新周期内新增单据，只能在草稿阶段点击“重新生成”
- 付款登记：`pur_ap_payment`
  - 支持登记付款、作废付款（会回写对账单 `paid_amount`，并自动更新对账单状态：已审核/部分已付/已结清）
- 发票登记：`pur_ap_invoice`
  - 支持登记发票、作废发票（会回写对账单 `invoice_amount`）

### P2：工程化增强（已落地：Excel + 单价权限隔离）

#### 采购订单 Excel 导出/导入（已落地）

- 后端接口
  - `GET /api/purchase/orders/export`（权限：`pur:order:export` + `pur:price:view`）
  - `GET /api/purchase/orders/import-template`（权限：`pur:order:export` + `pur:price:view`）
  - `POST /api/purchase/orders/import`（权限：`pur:order:import` + `pur:price:edit`）
- Excel 结构与规则
  - 导出按“采购订单明细行”扁平化输出（含供应商/订单/商品/数量/单价/金额/已入库等字段）
  - 导入按 `orderNo` 分组：同 `orderNo` 多行会合并为同一张采购订单；若 `orderNo` 为空则自动生成
  - 约束：`supplierCode`、`productCode` 必填；`qty > 0`；禁止与现有 `orderNo` 重复
  - 便于演示：导入后的采购订单默认置为“已审核”（后续可直接新增收货批次/对账）
- Flyway / 权限种子
  - `backend/src/main/resources/db/migration/V24__purchase_p2_excel_price_perms.sql`
  - 新增权限点：`pur:order:export`、`pur:order:import`（默认授予 `admin`）
- 前端入口
  - `采购管理 -> 采购订单` 页面顶部按钮：`导出 / 导入 / 模板`

#### 采购单价可见/可编辑权限隔离（已落地）

- 新增权限点（默认授予 `admin`）
  - `pur:price:view`：可见采购单价/金额/总额
  - `pur:price:edit`：可编辑采购单价（含 Excel 导入）
- 后端约束（避免绕过前端直接调接口查看单价）
  - 无 `pur:price:view` 时：`采购订单/采购退货` 返回的 `totalAmount/price/amount/payAmount` 等字段会置空
  - 涉及录入单价的入口统一要求 `pur:price:edit`：
    - 新建采购入库（自动生成采购订单）
    - 新建退货单
    - 创建采购订单接口
    - 采购订单 Excel 导入
- 前端交互
  - 无 `pur:price:view`：隐藏页面中的 `单价/金额/总额` 列
  - 无 `pur:price:edit`：隐藏/禁用涉及录入单价的入口（如：采购订单导入、新建退货单、新建采购入库）

#### 其他工程化项（待做）

- 审计字段统一化：引入 JPA Auditing（可选）
- 更强回归测试：并发入库/重复提交/网络重试场景

---

## 8. 快速验证（建议你怎么验收本阶段）

1) 使用 `admin/123456` 登录
2) `基础资料 -> 往来单位`：确保存在 `type=1` 的供应商（没有就新建一个）
3) `采购管理 -> 采购入库单`：
   - 新建采购入库（选供应商 + 选仓库 + 选商品 + 填单价/采购数量/本次入库数量）
   - 若分批到货：点击页面顶部“新增收货批次”，选择采购单并填写本次到货数量（若已有待检批次会弹窗提示）
4) `采购管理 -> 采购订单`：
   - 仅用于查看采购订单记录与明细（含已入库进度）
   - P2：可点击 `导出 / 导入 / 模板` 验证 Excel（需要对应权限点）
5) `采购管理 -> 采购退货单`：
   - 新建退货单（选供应商 + 选仓库 + 选商品 + 填单价/数量）
   - 审核
   - 执行（生成 WMS 出库单 type=2，并扣减库存，写入 PURCHASE_RETURN 流水）
6) `库存管理 -> 库存查询/库存流水`：
   - 新增收货批次后：`待质检` 会增加（`qcQty`），但 `物理库存/可用库存` 不变
   - 点击“质检通过”后：`物理库存/可用库存` 增加，同时 `待质检` 扣减
   - 流水应出现 `采购入库` / `采购退货`（显示中文类型）
7) `采购管理 -> 采购对账单`：
   - 新建对账单：选择供应商 + 对账周期（应自动汇总已完成入库/退货单据）
   - 审核后登记付款/发票：对账单应显示“已付/未付、已开票”，并随登记作废自动更新状态

---

## 9. 第四阶段已实现功能总览（截至 2026-02-05）

- 采购订单（PO，记录域）
  - 支持创建/审核/作废/入库进度回写（已入库数量 `inQty`），并在列表展示“部分入库/已完成”等状态
  - PO 页面只读展示；在 PO 详情中提供“新增收货批次”跳转入口（带出该 PO）
- 采购入库（分批收货 + IQC 质检门禁）
  - `新增收货批次`：从 PO 选择本次到货数量，生成“待质检批次单”（不直接增加物理库存）
  - `质检通过`：将待检库存转入物理库存，生成 WMS 入库单并写入库存流水（采购入库）
  - `不合格`：作废该批次并扣减待检库存，不影响物理库存
  - `冲销/红冲`：对“已完成入库批次”生成冲销 WMS 出库单，回滚库存与采购入库进度（若已进入对账单则禁止）
  - 关键约束：同一 PO 存在待检批次时，新建批次会弹窗提示待检汇总，避免“越点越多”误解库存不变
- 采购退货
  - 支持新建/审核/执行/作废；执行时生成 WMS 出库单、扣减库存，并写入库存流水（采购退货）
- 应付闭环（采购对账/付款/发票）
  - 对账单按“供应商 + 周期”汇总入库/退货单据，使用 `pur_ap_doc_ref` 防止重复占用
  - 对账单锁定：审核后不允许再新增单据；草稿阶段支持“重新生成”
  - 支持付款登记/作废、发票登记/作废，并回写对账单金额与状态
- P2 工程化落地
  - 采购订单 Excel 导出/导入（带权限点控制）
  - 采购单价 `可见/可编辑` 权限隔离（后端脱敏 + 前端隐藏/禁用入口）
