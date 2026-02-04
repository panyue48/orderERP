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
  - `entity/`：`PurOrder`、`PurOrderDetail`
  - `repository/`：`PurOrderRepository`、`PurOrderDetailRepository`
  - `service/`：`PurOrderService`
  - `controller/`：`PurOrderController`

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

- 列表：`GET /api/purchase/inbounds`
- 详情：`GET /api/purchase/inbounds/{id}`

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

### 3.4 与 WMS 的配合（关键点）

采购入库会写入 WMS 的通用出入库表：

- `wms_io_bill.type=1`：采购入库（Purchase In）
- `wms_io_bill.biz_no`：用于追溯关联（采购单号/入库单号）
- `wms_stock_log.biz_type='PURCHASE_IN'`：库存流水标识

幂等策略（当前实现）：

- 入库前查询 `wms_io_bill` 是否已存在 `findFirstByBizNoAndType(orderNo, 1)`
- 若已存在：直接返回 existing，并确保采购单状态被回写为已完成（不重复写库存/流水）

> 说明：`wms_io_bill` 在第三阶段引入了全表唯一索引 `unique(biz_id, type)`（用于冲销单幂等）。
> 为避免不同业务表的 ID 在 biz_id 上发生碰撞，采购域不使用 `biz_id` 作为外部幂等键，改用 `biz_no`/采购域自身的 requestNo。

---

## 4. 前端实现（已落地）

### 4.1 菜单路由

- 菜单：`采购管理 -> 采购订单`
- 组件映射：
  - 后端 sys_menu.component：`views/PurchaseOrders.vue`
  - 前端路由映射：`frontend/src/router/dynamic.ts`

### 4.2 页面

- 页面文件：`frontend/src/views/PurchaseOrders.vue`
- 支持：
  - 列表/搜索/分页
  - 新建采购单（选供应商、选商品、填单价数量）
  - 审核
  - 入库（选仓库 + 填本次入库数量，支持分批入库；默认填充剩余可入数量）
  - 作废
  - 详情查看（含明细）

### 4.2.1 采购入库单页面

- 页面文件：`frontend/src/views/PurchaseInbounds.vue`
- 支持：列表/搜索/分页、详情查看（含明细）

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

---

## 6. 已知限制（当前版本的“刻意简化”）

1) **入库已支持“分批入库”，但仍偏简化**

- 当前已支持：分批到货、部分入库、订单进度回写
- 仍缺少：收货差异（少到/多到）、补单/退货、质检（IQC）、对账/发票/付款等完整闭环

2) **采购模块功能较少**

- 当前仅有“采购订单”一个入口，缺少围绕采购的更多业务对象（收货单/退货/对账/付款/发票等）

---

## 7. 未来待拓展（按优先级，建议迭代顺序）

### P0（已落地）：分批入库 / 部分入库（更贴近真实场景）

- 已落库：`pur_inbound / pur_inbound_detail`
- 入库执行时生成 WMS 单：`wms_io_bill.type=1`，并用 `biz_no=pur_inbound.inbound_no` 做追溯关联
- 幂等：`requestNo(UUID)` + `pur_inbound.request_no` 唯一约束
- 回写采购单进度：
  - 明细层面：`pur_order_detail.in_qty += inbound_qty`
  - 主表状态：`status=3(部分入库)` / `status=4(已完成)`

### P0：采购入库冲销/红冲（修正错误入库）

目标：入库错误时可回滚库存与流水，且行为可审计。

可选做法：

- 复用第三阶段的“冲销”工程化方案，为 `wms_io_bill.type=1` 增加冲销入口与权限
- 或在采购域内提供“入库单冲销”，内部调用 WMS 的出库/冲销逻辑

### P1：采购退货（退供应商）

- 新增 `pur_return` / `pur_return_detail`
- 执行退货时生成 WMS 出库单（`wms_io_bill.type=...`，可复用 type=2/4 或新增类型，需统一规划）
- 同步写库存流水（例如 `PURCHASE_RETURN`）

### P1：质检（IQC）与入库质检状态

- 收货后进入“待质检”，质检通过才允许入库
- 不合格可走退货或让步接收

### P1：采购对账/发票/付款（与财务模块联动）

目标：采购完成后进入“对账 -> 付款 -> 已付/部分付”的资金闭环。

- 对账单（供应商维度周期结算）
- 发票登记（税额、发票号、开票日期）
- 付款单（对接第六阶段的资金收付）

### P2：工程化增强

- 导出/导入：采购订单 Excel 导出/导入（与第二阶段 Excel 风格一致，权限点控制）
- 审计字段统一化：引入 JPA Auditing（可选）
- 更细粒度权限：采购单价可见/可编辑权限隔离
- 更强回归测试：并发入库/重复提交/网络重试场景

---

## 8. 快速验证（建议你怎么验收本阶段）

1) 使用 `admin/123456` 登录
2) `基础资料 -> 往来单位`：确保存在 `type=1` 的供应商（没有就新建一个）
3) `采购管理 -> 采购订单`：
   - 新建采购单（选供应商 + 选商品 + 填单价/数量）
   - 审核
   - 入库（选仓库）
4) `库存管理 -> 库存查询/库存流水`：
   - 库存应增加
   - 流水应出现 `PURCHASE_IN`
