本文用于记录 **OrderERP 第六阶段（Finance 财务核销）** 的已实现内容与后续可拓展方向，方便后续迭代与排查问题。

> 设计口径：本项目第 4/5 阶段已形成「采购应付 AP 对账单」「销售应收 AR 对账单」闭环，因此 Finance 阶段的 P0 重点是把“钱”的流向统一沉淀为 **资金账户 + 收付款流水**，并与 AP/AR 的“登记付款/登记收款”打通。

---

## 1. P0 最小闭环（已落地）

### 1.1 数据模型（Flyway）

- 迁移：`backend/src/main/resources/db/migration/V39__finance_p0.sql`

新增两张表：

1) `fin_account`（资金账户）
- 字段：`account_name / account_no / balance / status / remark / deleted / create_* / update_*`
- 约束：`uk_fin_account_name(account_name)`，避免同名账户重复
- 种子数据：默认插入「公司基本户」便于本地测试

2) `fin_payment`（收付款流水）
- 字段：`pay_no / type(1收款2付款) / partner_id / account_id / amount / biz_type / biz_id / biz_no / pay_date / method / remark / status / create_* / cancel_*`
- 约束：
  - `uk_fin_payment_pay_no(pay_no)`：流水号全局唯一
  - `uk_fin_payment_biz(biz_type, biz_id)`：保证“来源记录”只会生成 1 条资金流水（幂等）

### 1.2 后端接口（已落地）

#### 1) 资金账户

- 列表：`GET /api/finance/accounts`（权限：`fin:account:view`）
- 下拉选项：`GET /api/finance/accounts/options`（权限：`fin:account:view`）
- 新增：`POST /api/finance/accounts`（权限：`fin:account:add`）

#### 2) 收付款流水

- 列表：`GET /api/finance/payments`（权限：`fin:payment:view`）
  - 支持过滤：`type / accountId / partnerId / startDate / endDate / keyword`

### 1.3 前端页面（已落地）

菜单：`财务管理`

- `资金账户`：`frontend/src/views/FinanceAccounts.vue`
  - 支持：查询、分页、新增资金账户（期初余额）
- `收付款流水`：`frontend/src/views/FinancePayments.vue`
  - 支持：按类型/账户/日期/关键字筛选查看

> 注意：动态路由映射已登记：
> - `frontend/src/router/dynamic.ts`

---

## 2. 与业务域联动（已落地）

### 2.1 销售应收（AR）登记收款 -> 资金流水

入口：`销售管理 -> 销售对账单 -> 登记收款`

- 业务表：`sal_ar_receipt`
- Finance 落库：生成 `fin_payment`
  - `type = 1(收款)`
  - `biz_type = 1(销售收款)`
  - `biz_id = 收款记录ID`
  - `pay_no = receipt_no`
  - `biz_no = AR对账单号`
- 账户余额：`fin_account.balance += amount`
- 作废收款：回滚余额，并将 `fin_payment.status = 9`

相关代码：
- `backend/src/main/java/com/ordererp/backend/sales/service/SalArBillService.java`
- `backend/src/main/java/com/ordererp/backend/finance/service/FinPaymentService.java`

### 2.2 采购应付（AP）登记付款 -> 资金流水

入口：`采购管理 -> 采购对账单 -> 登记付款`

- 业务表：`pur_ap_payment`
- Finance 落库：生成 `fin_payment`
  - `type = 2(付款)`
  - `biz_type = 2(采购付款)`
  - `biz_id = 付款记录ID`
  - `pay_no = pay_no`
  - `biz_no = AP对账单号`
- 账户余额：`fin_account.balance -= amount`
  - 关键约束：余额不足时禁止登记付款（避免资金为负）
- 作废付款：回滚余额，并将 `fin_payment.status = 9`

相关代码：
- `backend/src/main/java/com/ordererp/backend/purchase/service/PurApBillService.java`
- `backend/src/main/java/com/ordererp/backend/finance/service/FinPaymentService.java`

---

## 3. 更符合企业心智的交互（已落地）

### 3.1 在登记收款/付款时选择资金账户

- 销售对账单登记收款弹窗：增加「收款账户」下拉
  - `frontend/src/views/SalesArBills.vue`
- 采购对账单登记付款弹窗：增加「付款账户」下拉
  - `frontend/src/views/PurchaseApBills.vue`

说明：
- 若用户无 `fin:account:view` 权限导致无法加载账户下拉，前端会隐藏下拉；后端会使用“默认启用账户”兜底。
- 若系统不存在任何启用账户，后端会提示先创建资金账户。

---

## 4. 如何验证（推荐回归顺序）

1) 确认迁移已执行：数据库存在 `fin_account/fin_payment` 两张表，并且有默认账户
2) `财务管理 -> 资金账户`：新增 1 个账户（例如“现金”），余额应为期初余额
3) `销售管理 -> 销售对账单`：
   - 审核后登记收款，选择“现金”账户
   - 回到 `财务管理 -> 收付款流水`：应出现 1 条“收款”记录，且关联单据号为 AR 对账单号
   - 回到 `资金账户`：现金余额应增加
   - 作废该收款：流水变“作废”，现金余额回滚
4) `采购管理 -> 采购对账单`：
   - 审核后登记付款，选择“现金”账户（余额不足时应直接报错）
   - 流水应出现 1 条“付款”记录，账户余额减少
   - 作废付款：余额回滚

---

## 5. 待拓展（建议优先级）

### P1（业务补齐）
- 资金账户：停用/启用、编辑账户信息
- 资金流水：支持“红冲/冲销流水”（代替直接作废）、支持附件（回单/凭证图片）
- 对账核销：支持“部分核销/多单核销”（一个收款对应多个 AR，对应真实企业的核销习惯）

### P1（已补齐：手工收付款 / 资金调拨）

- 迁移：`backend/src/main/resources/db/migration/V40__finance_p1_manual_transfer.sql`
- 手工收付款：
  - 接口：`POST /api/finance/manual-payments`、`POST /api/finance/manual-payments/{id}/cancel`
  - 权限：`fin:payment:manual`
  - 资金流水：写入 `fin_payment.biz_type=5(手工收款)/6(手工付款)`，并更新账户余额
- 资金调拨（账户间转账）：
  - 接口：`POST /api/finance/transfers`、`POST /api/finance/transfers/{id}/cancel`
  - 权限：`fin:transfer:add`、`fin:transfer:cancel`
  - 资金流水：生成两条流水（转出付款 biz_type=3 + 转入收款 biz_type=4），并对两个账户分别增减余额

前端：
- `frontend/src/views/FinancePayments.vue` 增加：
  - `手工收付款` 弹窗
  - `资金调拨` 弹窗
  - 对手工/调拨流水提供行内“作废”操作（会回滚余额）

### P2（工程化）
- 幂等增强：为 `fin_payment` 增加 `request_no` 唯一键，支持前端重试无重复入账
- 审计与对账：定时任务校验 `fin_account.balance` 与 `fin_payment` 汇总一致性（防数据漂移）
- 权限细分：按账户维度权限（某些用户只能看“现金”不能看“银行”）
