# orderERP

企业级 ERP/进销存练习项目（前后端分离）。当前已完成 **第一阶段（System Core）**、**第二阶段（Base Data）**、**第三阶段（WMS Core）** 的核心能力，并配套了基于 **Testcontainers + MySQL** 的集成测试用例。

---

## 1. 项目结构

```
orderERP/
├── backend/                 # Spring Boot 后端（JPA + JWT + Flyway）
│   ├── src/main/java/...    # 业务代码（system/base/wms/ledger）
│   ├── src/main/resources/  # application.yml + Flyway 迁移脚本 db/migration
│   └── src/test/java/...    # Testcontainers 集成测试（阶段 1/2/3）
├── frontend/                # Vue3 前端（Vite + Pinia + Element Plus）
├── uploads/                 # 本地上传目录（开发环境）
├── scripts/                 # 脚本（例如演示数据手动导入）
└── *.md                     # 阶段文档/问题复盘/工程化记录
```

---

## 2. 技术栈（以当前工程为准）

### 2.1 后端（backend）
- Java + Spring Boot 3.x
- Spring Security（JWT 无状态认证）
- JJWT（Token 生成/解析）
- Spring Data JPA（Repository/Entity）
- Flyway（数据库迁移与种子数据）
- EasyExcel（Excel 导入/导出）
- Springdoc OpenAPI（Swagger UI）
- Actuator（健康检查等）
- 数据库：MySQL 8.x（开发/测试），测试可用 Testcontainers 拉起临时 MySQL

### 2.2 前端（frontend）
- Vue 3 + Vite
- Pinia（状态/权限集合）
- Vue Router（动态路由）
- Element Plus（UI 组件）

---

## 3. 阶段进度与功能清单

### 3.1 第一阶段：System Core（系统地基）

目标：跑通 **登录/JWT**、**权限校验**、**菜单动态加载（千人千面）**。

已实现（关键点）：
- 认证：`POST /api/auth/login`
  - 通过 Spring Security `AuthenticationManager` 完成用户名密码认证
  - 生成 JWT 并返回给前端
- JWT 鉴权：`JwtAuthenticationFilter`
  - 读取 `Authorization: Bearer <token>`，解析 username，加载用户并注入 SecurityContext
- RBAC 权限模型（表结构与初始化）：`sys_user/sys_role/sys_menu/sys_user_role/sys_role_menu`
  - 迁移脚本：`backend/src/main/resources/db/migration/V1__init.sql`
  - 默认用户：`admin / 123456`（历史数据兼容：明文密码会自动按 `{noop}` 处理）
- 动态菜单路由：`GET /api/system/menu/routers`
  - 后端根据用户 id 返回可访问菜单树（用于前端动态路由构建）
- 个人信息与权限集合：
  - `GET /api/system/user/profile`
  - `PUT /api/system/user/profile`
  - `POST /api/system/user/profile/password`
  - `GET /api/system/user/perms`（前端按钮级权限控制基础）

对应测试：
- `backend/src/test/java/com/ordererp/backend/system/SystemStage1IT.java`

---

### 3.2 第二阶段：Base Data（基础资料）

目标：补齐 ERP 运行所需“静态基础资料”，并提供 Excel 导入导出能力。

已实现（关键点）：
- 商品（`base_product`）CRUD + options 下拉
  - `GET /api/base/products`
  - `GET /api/base/products/{id}`
  - `GET /api/base/products/options`
  - `POST /api/base/products`
  - `PUT /api/base/products/{id}`
  - `DELETE /api/base/products/{id}`
  - 唯一键策略：`product_code` 唯一；逻辑删除后再次创建同编码时“复活”旧记录（避免唯一约束冲突）
- 仓库（`base_warehouse`）CRUD + options 下拉
- 往来单位（`base_partner`）CRUD + options 下拉
- Excel 能力（EasyExcel）：
  - 导出：`GET /api/base/*/export`
  - 模板：`GET /api/base/*/import-template`
  - 导入：`POST /api/base/*/import`（multipart file）
  - 下载体验：响应头与文件名编码通过 `ExcelHttpUtil` 统一处理

对应测试：
- `backend/src/test/java/com/ordererp/backend/base/BaseStage2IT.java`

---

### 3.3 第三阶段：WMS Core（库存核心）

目标：建立可追溯的库存账本，通过 WMS 单据变更库存（先不依赖订单）。

核心数据模型：
- `wms_stock`：实时库存（`stock_qty/locked_qty/version`，可用库存 = `stock_qty - locked_qty`）
- `wms_io_bill` + `wms_io_bill_detail`：盘点入库/出库单据（type=3/4）
- `wms_stock_log`：库存流水/审计日志（变更类型、业务单号、变更后库存）

已实现（关键点）：
- 库存查询：
  - `GET /api/wms/stocks`（返回 `stockQty/lockedQty/availableQty`）
  - 导出：`GET /api/wms/stocks/export`（已做分页分批写入，避免一次性加载）
- 盘点入库（type=3）：
  - 列表/详情/执行前校验/创建/执行/冲销
  - 执行/冲销在事务中完成：库存变更 + 明细 real_qty + 写入流水
- 盘点出库（type=4）：
  - 列表/详情/执行前校验/创建/执行/冲销
- 库存流水：
  - `GET /api/wms/stock-logs`（支持 keyword/warehouseId/productId + startTime/endTime）
  - 导出：`GET /api/wms/stock-logs/export`（分页分批写入）
- 工程化增强（对应文档第 6 章落地）：
  - 幂等与并发防护：
    - 反复执行：`execute` 已完成时直接返回“已完成结果”
    - 反复冲销：优先返回已存在的冲销单
    - DB 唯一约束：`wms_io_bill(biz_id, type)` 防止并发重复冲销（迁移 `V12__wms_unique_reversal.sql`）
    - 单据行锁：执行/冲销入口对单据主表使用 `PESSIMISTIC_WRITE`（串行化同单据写入）
  - 数据一致性：库存不变量校验（`stock_qty>=0`、`locked_qty>=0`、`locked_qty<=stock_qty`）
  - 盘点模型升级（实盘数量 counted_qty）：
    - 新增 `wms_check_bill / wms_check_bill_detail`
    - 执行盘点单时自动生成调整单（type=3/4）并写 `CHECK_ADJUST_IN/OUT` 流水
    - 迁移 `V13__wms_check_bill.sql`，接口 `/api/wms/check-bills`
  - 演示数据隔离：提供手动脚本 `scripts/seed-demo.sql`（不强绑生产迁移）

对应测试：
- `backend/src/test/java/com/ordererp/backend/wms/WmsStage3HardeningIT.java`

---

## 4. 快速开始

### 4.1 准备环境
- JDK：建议 17+（后端 pom 以 17 为基准）
- Maven 3.x
- Node.js（前端开发）
- MySQL 8.x（本地开发），或使用 Docker + Testcontainers（仅测试需要）

### 4.2 启动后端
```
cd backend
mvn spring-boot:run
```

### 4.3 启动前端
```
cd frontend
npm install
npm run dev
```

### 4.4 默认登录账号
- `admin / 123456`

---

## 5. 数据库与 Flyway

- Flyway 默认启用，迁移脚本位置：`backend/src/main/resources/db/migration/`
- 后端启动会自动 migrate（包含初始化数据/菜单/权限点）
- `DatabaseGuardConfig` 会校验“当前连接的 schema 名称”，避免误连错误库（例如把表建到其他 schema）。

如需修改数据库连接，编辑：
- `backend/src/main/resources/application.yml`

---

## 6. 运行测试（阶段 1/2/3）

前置条件：
- Docker Desktop 正常运行
- 网络能拉取镜像（至少 `mysql:8.0.36`、`testcontainers/ryuk`）

命令：
```
cd backend

# 第一阶段
mvn test "-Dtest=SystemStage1IT"

# 第二阶段
mvn test "-Dtest=BaseStage2IT"

# 第三阶段
mvn test "-Dtest=WmsStage3HardeningIT"

# 全部测试
mvn test
```

---



