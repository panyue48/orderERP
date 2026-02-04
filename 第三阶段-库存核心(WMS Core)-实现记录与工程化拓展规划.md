# 第三阶段：库存核心（WMS Core）实现记录与工程化拓展规划

本文用于把 **OrderERP 第三阶段（WMS Core / 库存核心）** 已实现的技术与功能做一次“可复盘的完整记录”，并给出后续在 **工程化** 与 **健壮性** 方向的可落地拓展方案（包含建议的数据库迁移/接口/代码结构）。

> 说明：本阶段设计参考 `erp_data.sql` 中的 WMS 表结构与语义（`wms_stock / wms_io_bill / wms_io_bill_detail / wms_stock_log`）。

---

## 1. 技术栈与工程约束

### 1.1 后端

- Spring Boot 3.x
- Spring Security + JWT（接口权限依赖 `@PreAuthorize`）
- Spring Data JPA（实体/仓储/事务）
- Flyway（迁移：V8 ~ V11）
- EasyExcel（库存/流水导出 xlsx）
- 事务与并发：
  - 业务写操作均在 `@Transactional` 中执行（出入库执行/冲销）
  - `wms_stock` 使用 JPA `@Version` 字段实现乐观锁并做少量重试
  - 全局异常处理将乐观锁冲突映射为 `409 CONFLICT`（提示“请重试”）

### 1.2 前端

- Vue 3 + Vite
- Pinia（权限集合 `perms` + `hasPerm`）
- Element Plus（表格、弹窗、确认、导出提示等）
- 动态路由：后端返回 `sys_menu.component`，前端在 `frontend/src/router/dynamic.ts` 做组件映射
- 权限可视化：角色管理页用树形展示 `sys_menu`（包含页面权限与按钮权限）

---

## 2. 数据模型（对齐 `erp_data.sql`）

### 2.1 `wms_stock`（实时库存核心表）

- 关键字段：
  - `stock_qty`：物理库存
  - `locked_qty`：锁定库存（预留给后续销售锁库等场景）
  - `version`：乐观锁版本号
- 计算公式：
  - **可用库存 = stock_qty - locked_qty**

### 2.2 `wms_io_bill`（出入库单据主表）

- 核心字段：
  - `bill_no`：单据编号
  - `type`：类型（本阶段主要落地）
    - `3`：盘点入库（库存调整 +）
    - `4`：盘点出库（库存调整 -）
  - `status`：
    - `1`：待执行
    - `2`：已完成
  - `biz_id / biz_no`：
    - 用于“冲销单”关联原单（冲销单写 `biz_id=原单id`、`biz_no=原单bill_no`）

### 2.3 `wms_io_bill_detail`（出入库单据明细）

- `qty`：计划数量
- `real_qty`：实际执行数量（本阶段执行后写入；同时用于防重复执行检测）

### 2.4 `wms_stock_log`（库存流水/审计日志）

- `biz_type`：流水类型（本阶段使用）
  - `STOCK_IN`：盘点入库执行
  - `STOCK_OUT`：盘点出库执行
  - `REVERSAL_IN`：冲销盘点入库（实际扣减库存）
  - `REVERSAL_OUT`：冲销盘点出库（实际增加库存）
- `change_qty`：变动数量（入库为正，出库/冲销入库为负）
- `after_stock_qty`：变动后物理库存（便于快速审计）

---

## 3. 后端功能实现清单（接口 + 业务规则）

### 3.1 库存查询

- 接口：
  - `GET /api/wms/stocks`
- 返回：
  - 通过联表/投影返回 `warehouseName/productCode/productName/unit` 等展示字段
  - 返回 `stockQty/lockedQty/availableQty`（服务端计算）
- 权限：
  - `wms:stock:view`

### 3.2 盘点入库（type=3）

- 接口：
  - 列表：`GET /api/wms/stock-in-bills`
  - 详情：`GET /api/wms/stock-in-bills/{id}`
  - 执行前校验：`GET /api/wms/stock-in-bills/{id}/precheck`
  - 创建：`POST /api/wms/stock-in-bills`
  - 执行：`POST /api/wms/stock-in-bills/{id}/execute`
  - 冲销：`POST /api/wms/stock-in-bills/{id}/reverse`
- 关键校验（创建/执行）：
  - 仓库必须存在且启用
  - 商品必须存在且启用
  - 明细必须去重（同一单据同商品不允许重复）
  - `qty > 0`
  - 细化防重复：
    - `real_qty > 0` 视为已执行（拒绝再次执行）
- 执行逻辑：
  - 按明细逐行：
    - upsert `wms_stock`（不存在则创建，存在则 `stock_qty += qty`）
    - `real_qty = qty`
    - 写 `wms_stock_log`（`STOCK_IN`，`change_qty=+qty`）
  - 单据置为 `status=2`
- 冲销逻辑（生成冲销单，不修改原单）：
  - 前置条件：原单必须 `status=2` 且不是冲销单（`biz_id` 为空）
  - 若已存在冲销单（通过 `biz_id + type` 查找）则直接返回既有冲销单（幂等行为）
  - 生成一张冲销单（type=4，`bill_no` 前缀 `RSI`，`status=2`，`biz_id/biz_no` 关联原单）
  - 按原单明细数量扣减库存（等价于出库）并写 `REVERSAL_IN` 流水
  - 冲销时会校验可用库存是否足够，否则拒绝冲销
- 权限：
  - 查看：`wms:stockin:view`
  - 新增：`wms:stockin:add`
  - 执行：`wms:stockin:execute`
  - 冲销：`wms:stockin:reverse`

### 3.3 盘点出库（type=4）

- 接口：
  - 列表：`GET /api/wms/stock-out-bills`
  - 详情：`GET /api/wms/stock-out-bills/{id}`
  - 执行前校验：`GET /api/wms/stock-out-bills/{id}/precheck`
  - 创建：`POST /api/wms/stock-out-bills`
  - 执行：`POST /api/wms/stock-out-bills/{id}/execute`
  - 冲销：`POST /api/wms/stock-out-bills/{id}/reverse`
- 关键校验（执行前校验更细）：
  - 除“入库”的校验项外，出库还会对每行返回：
    - `stockQty/lockedQty/availableQty`
    - 不足时给出失败原因（例如 `insufficient stock`、`stock not found`）
  - 执行时再次校验（不信任前端）
- 执行逻辑：
  - 按明细逐行：
    - 校验库存存在
    - 校验 `locked_qty <= stock_qty` 且 `available >= qty`
    - `stock_qty -= qty`
    - `real_qty = qty`
    - 写 `wms_stock_log`（`STOCK_OUT`，`change_qty=-qty`）
  - 单据置为 `status=2`
- 冲销逻辑：
  - 原单必须 `status=2` 且不是冲销单
  - 若已存在冲销单（通过 `biz_id + type` 查找）则复用
  - 生成冲销单（type=3，`bill_no` 前缀 `RSO`，`status=2`）
  - 按原单明细数量增加库存并写 `REVERSAL_OUT` 流水
- 权限：
  - 查看：`wms:stockout:view`
  - 新增：`wms:stockout:add`
  - 执行：`wms:stockout:execute`
  - 冲销：`wms:stockout:reverse`

### 3.4 库存流水查询

- 接口：
  - `GET /api/wms/stock-logs`
- 支持筛选：
  - `keyword`（biz_no / biz_type）
  - `warehouseId`
  - `productId`
- 权限：
  - `wms:stocklog:view`

### 3.5 Excel 导出（库存/流水）

- 库存导出：
  - `GET /api/wms/stocks/export`（xlsx）
  - 权限：`wms:stock:export`
- 流水导出：
  - `GET /api/wms/stock-logs/export`（xlsx）
  - 权限：`wms:stocklog:export`

---

## 4. 前端功能实现清单（页面 + 交互）

### 4.1 库存查询（`/wms/stock`）

- 支持仓库下拉筛选、关键字搜索
- 支持导出按钮（按权限显示）

### 4.2 盘点入库 / 盘点出库（`/wms/stock-in`、`/wms/stock-out`）

- 列表 + 详情
- 执行改为“先校验后确认”的弹窗流程：
  1) 点击“执行”
  2) 弹出“执行前校验”对话框，展示逐行校验结果
  3) 只有校验通过才允许点击“确认执行”
- 冲销操作：
  - 对已完成单据显示“冲销”按钮（按权限显示）

### 4.3 库存流水（`/wms/stock-log`）

- 支持仓库/商品下拉 + 关键词筛选
- `bizType` 在 UI 做了可读映射（盘点/冲销）
- 支持导出按钮（按权限显示）

### 4.4 角色权限树展示修复（按钮权限可见）

由于按钮/功能权限是 `menu_type='F'` 且常见 `visible=0`，原先权限树按 `visible` 过滤会导致“导出/冲销”等权限点不出现。

- 修复：角色管理页对 `menu_type='F'` 放开显示（即使 `visible=0`）

---

## 5. Flyway 迁移记录（WMS Core）

- `V8__wms_core.sql`
  - 建 WMS 表：`wms_stock / wms_io_bill / wms_io_bill_detail / wms_stock_log`
  - 新增菜单：库存查询/盘点入库
- `V9__wms_more_menus.sql`
  - 新增菜单：盘点出库/库存流水
  - 新增按钮权限：出库新增/出库执行
- `V10__wms_check_demo_data.sql`
  - 菜单名称统一为“盘点入库/盘点出库”
  - 写入演示仓库/商品/库存/单据/流水（用于快速体验与演示）
- `V11__wms_export_reverse_permissions.sql`
  - 新增权限：库存导出、流水导出、入库冲销、出库冲销，并授权给角色

---

## 6. 未来工程化与健壮性拓展：可落地实施方案

下面是“建议继续加强”的方向，每条都给出推荐的落地方式与涉及改动点，便于按迭代逐步上线。

### 6.1 并发与幂等（强一致防重复）

**问题场景**

- 反复点击“执行/冲销”或网络重试会重复请求
- 并发情况下可能出现重复生成冲销单（极端情况下）

**推荐实现**

1) **数据库层唯一约束防重复冲销**
   - 为 `wms_io_bill` 增加唯一索引：`unique(biz_id, type)`
   - 语义：同一原单（biz_id）同一种“冲销类型”（type）最多只能有一张冲销单
   - 迁移方式：新增 `V12__wms_unique_reversal.sql`（示例）
     - `alter table wms_io_bill add unique key uk_wms_io_bill_biz_type (biz_id, type);`

2) **执行/冲销接口幂等化**
   - 执行：以 `status` 作为幂等条件更新
     - `update wms_io_bill set status=2 where id=? and status=1`
     - 若 affected rows = 0 则表示已被执行（直接返回已完成状态）
   - 明细：执行成功后统一把 `real_qty` 写入；再次执行时发现 `real_qty>0` 直接拒绝或返回已完成

3) **对单据主表加悲观锁（可选）**
   - 在执行/冲销入口对 `wms_io_bill` 做 `PESSIMISTIC_WRITE` 行锁，串行化同一单据的写入
   - 适合高并发或对一致性要求更极端的场景

### 6.2 数据一致性约束（防脏数据）

1) **库存不变量**
   - `stock_qty >= 0`
   - `locked_qty >= 0`
   - `locked_qty <= stock_qty`
   - 落地方式：
     - 应用层执行前后校验 + 拒绝写入
     - 或数据库 `CHECK` 约束（MySQL 8 可用，仍建议先在测试库验证兼容）

2) **明细与主表关联约束**
   - `wms_io_bill_detail.bill_id` 需要索引（已存在），后续可考虑外键约束（若你确认不会影响历史数据/软删除策略）

### 6.3 导出能力可扩展（大数据量/性能）

当前导出做法是一次性取 `PageRequest(0, 50_000)`。

推荐增强（按优先级）：

1) **分页分批导出**
   - 循环分页查询，分批写入 EasyExcel sheet（避免一次性加载）
2) **时间范围过滤（流水必备）**
   - `/api/wms/stock-logs` 增加 `startTime/endTime`（或日期）参数
3) **异步导出任务（可选）**
   - 导出生成文件存 `uploads/exports`，前端轮询任务状态并提供下载链接
   - 好处：大数据导出不会阻塞请求线程

### 6.4 “盘点”的业务模型升级（从“增减数量”到“实盘数量”）

更贴近实际仓库盘点的做法：

- 录入 **实盘数量**（counted_qty）
- 系统读取账面数量（stock_qty）
- 计算差异：
  - `diff = counted_qty - stock_qty`
  - diff>0 生成入库调整；diff<0 生成出库调整

落地建议：

1) 新增盘点单据字段/新表（推荐新表更清晰）
   - `wms_check_bill / wms_check_bill_detail(counted_qty, diff_qty)`
2) “盘点单执行”自动生成 `wms_io_bill(type=3/4)` 作为调整单据（可追溯）
3) 流水 `biz_type` 增加 `CHECK_ADJUST_IN/OUT`（或沿用 STOCK_IN/OUT 并写入更细分信息）

### 6.5 演示数据隔离（避免污染生产）

目前演示数据在 `V10__wms_check_demo_data.sql` 中随迁移落库，适合开发/演示，但不适合生产。

推荐两种方式：

1) **Profile 分离 Flyway locations**
   - `application-dev.yml`：`spring.flyway.locations=classpath:db/migration,classpath:db/migration-dev`
   - 把 demo SQL 挪到 `db/migration-dev`
2) **提供手动初始化脚本**
   - 用独立 `scripts/seed-demo.sql`，需要演示时手动执行

### 6.6 自动化测试与回归防护

当前主要是启动测试，建议补充：

- Service 层用例：
  - 出库库存不足、锁定量异常、重复执行、冲销库存不足、重复冲销幂等等
- 使用 Testcontainers MySQL（可选但强烈推荐）
  - 避免测试依赖本机 `erp_data`，让 CI/多人环境可重复

---

## 7. 快速验证清单（给后续迭代回归用）

- 登录 `admin/123456`
- 库存管理：
  - 库存查询：能看到 `stockQty/lockedQty/availableQty`，可导出（有权限时）
  - 盘点入库/出库：
    - 点击执行：先出“执行前校验”弹窗，校验通过才可执行
    - 已完成单据可冲销（有权限时），冲销后库存与流水正确
  - 库存流水：能筛选并可导出（有权限时），能看到 `STOCK_*` 与 `REVERSAL_*` 流水

---

## 8. 问答（Q&A）与后续上线优化（按优先级）

> 本节用于把“已实现的闭环点”和“后续可选增强点”记录下来，方便你未来上线/多人协作时按优先级继续演进。

### Q1：盘点单是否已形成“菜单-权限-前端-后端”闭环？

- 已闭环：
  - 后端接口：`/api/wms/check-bills`（列表/详情/创建/执行）
  - 权限点：`wms:check:view`、`wms:check:add`、`wms:check:execute`
  - 菜单与授权（Flyway）：`V14__wms_check_bill_menu_permissions.sql`
  - 前端页面：`frontend/src/views/WmsCheckBills.vue`（列表/创建/执行/详情）

### Q2：为什么“演示数据”建议与生产环境隔离？怎么做最稳妥？

- 原因：演示数据一旦随通用迁移落库，生产初始化/回放迁移时会直接把 demo 数据写进生产库，属于高风险。
- 推荐（P0）：
  1) **Profile 分离 Flyway locations**（生产不加载 demo migrations）
  2) 或 **手动 seed 脚本**（只在演示时执行 `scripts/seed-demo.sql`）
- 建议上线前把 `V10__wms_check_demo_data.sql` 的执行范围明确为 dev/demo 环境，生产库建议用“干净新库 + 仅正式迁移”初始化。

### Q3：库存不变量目前在应用层校验，是否需要下沉到数据库？

- 可选增强（P1）：在 `wms_stock` 增加数据库层 `CHECK` 约束（并在测试库先验证兼容性）。
- 理由：应用层校验能拦大部分问题，但 DB 约束是最后一道“兜底防线”，能减少脏数据进入库的可能性。

### Q4：导出已经分页写出，后续还需要怎么扩展？

- 可选增强（P1）：做“异步导出任务”
  - 导出生成文件存 `uploads/exports`
  - 前端轮询任务状态并提供下载链接
  - 好处：大数据量导出不会占用请求线程/不会因为网络波动导致导出失败。

### Q5：并发一致性还需要更强的回归保障吗？

- 可选增强（P2）：补充并发回归测试（多线程同时执行/同时冲销）
  - 验证唯一约束 `unique(biz_id,type)` + “读取 existing 兜底”在真实并发下仍返回同一张关联单
  - 验证库存最终值与流水条数符合预期（不重复写入）。
