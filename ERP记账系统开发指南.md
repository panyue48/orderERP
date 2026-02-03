- - # 企业级 ERP (V4.0) 开发指南

    ## 1. 项目架构概述

    基于 `erp_data` 数据库结构，本项目采用标准的 **DDD (领域驱动设计)** 分层思想进行模块化开发。

    ### 技术栈确认

    - **后端**: Spring Boot 3.x + Spring Data JPA + Spring Security (JWT) + Flyway
      - 其他：Springdoc OpenAPI、Actuator、JJWT
    - **前端**: Vue 3 (Script Setup) + Vite + Pinia + Element Plus + Vue Router
    - **数据库**: MySQL 8.0 (`erp_data`)
    - **工具**: Lombok, EasyExcel (导入导出)
    
    ## 2. 模块划分与包结构建议
    
    建议后端包结构严格对应数据库表的前缀：
    
    ```
    com.ordererp.backend
    ├── common          // 全局通用 (exception/dto/util/upload)
    ├── config          // 配置类 (SecurityConfig, DatabaseGuardConfig, ...)
    ├── system          // [对应 sys_* 表] 用户、角色、菜单、权限
    ├── base            // [对应 base_* 表] 商品、仓库、往来单位
    ├── wms             // [对应 wms_* 表] 库存核心、出入库单、库存流水
    ├── ledger          // 记账/台账模块（当前演示）
    ├── purchase        // [对应 pur_* 表] 采购业务（规划）
    ├── sales           // [对应 sal_* 表] 销售业务（规划）
    ├── finance         // [对应 fin_* 表] 资金收付（规划）
    └── OrderErpApplication.java
    ```
    
    ## 3. 开发详细流程 (分阶段实施)
  
    ### 第一阶段：地基搭建 (System Core)
  
    **目标**：跑通登录、权限验证、菜单动态加载。
    
    1. **数据访问层（以实际工程为准）**：使用 Spring Data JPA 建模。
       - Entity：`@Entity` + `@Table` + `@Column`
       - Repository：`extends JpaRepository<..., Long>`，必要时用 `@Query`（JPQL/Native）做联表/投影
       - 逻辑删除：以 `deleted` 字段为准（在 Repository 查询条件中过滤），不依赖 MyBatis-Plus 的 `@TableLogic`
    2. **安全配置**:
       - 实现 `UserDetailsService`：查询 `sys_user`，并通过 `sys_user_role` -> `sys_role_menu` -> `sys_menu` 加载权限列表 (Perms)。
       - JWT 过滤器：解析 Token，注入 SecurityContext。
    3. **前端路由**:
       - 调用 `/system/menu/routers` 接口，前端递归生成 Vue Router 路由表，实现“千人千面”的菜单。
    
    ### 第二阶段：基础数据 (Base Data)
    
    **目标**：完成 ERP 运行所需的静态数据。
    
    1. **商品管理 (`base_product`)**:
       - 重点：SKU 编码唯一性校验。
       - 前端：使用 Upload 组件处理图片上传。
    2. **仓库与伙伴**:
       - 简单的 CRUD。
    3. **Excel 导入导出（以实际工程为准）**：后端使用 EasyExcel 提供导出与模板下载（并与权限点联动）。
    
    ### 第三阶段：库存核心 (WMS Core) - **最关键**
    
    **目标**：建立准确的库存账本，实现“只进不出”逻辑（此时不通过订单，只通过 WMS 单据变动）。
  
    1. **库存查询**:
       - 联表查询 `wms_stock` 和 `base_product`，展示每个仓库的 `stock_qty` (物理库存) 和 `locked_qty` (锁定库存)。
       - **计算公式**: `可用库存 = stock_qty - locked_qty`。
    2. **乐观锁配置**:
       - 以 JPA 为准：确保 `WmsStock` 实体类的 `version` 字段加了 `@Version` 注解。
       - 并发冲突时捕获 `ObjectOptimisticLockingFailureException`（或做少量重试），提示用户“请重试”。
  
    ### 第四阶段：采购业务 (Purchase)
  
    **目标**：实现“进货 -> 入库 -> 加库存”闭环。
  
    1. **采购单据流转**:
       - **草稿**: 仅保存 `pur_order` 和 `pur_order_detail`。
       - **审核**: 修改状态 `status = 2`。
       - **入库**:
         1. 生成 `wms_io_bill` (类型=采购入库)。
         2. 更新 `wms_stock`：`stock_qty = stock_qty + qty`。
         3. 记录 `wms_stock_log`。
         4. 回写采购单状态 `status = 4 (已完成)`。
  
    ### 第五阶段：销售业务 (Sales) - **逻辑最复杂**
    
    **目标**：实现“下单 -> 锁库 -> 发货 -> 扣库”闭环。
    
    **核心逻辑链 (必须在事务中)**:
    
    1. **创建订单 (快照机制)**:
       - 查询 `base_product`，将 `product_name`, `unit` 等信息**复制**到 `sal_order_detail` 表。
       - *切记不要只存 ID，否则以后商品改名，历史订单会乱。*
    2. **审核通过 (锁定库存)**:
       - 遍历订单明细。
       - 执行 `UPDATE wms_stock SET locked_qty = locked_qty + ? WHERE product_id = ? AND warehouse_id = ?`。
       - 如果 `可用库存 < 0`，抛出异常“库存不足”。
    3. **确认发货 (扣减实物)**:
       - 生成 `wms_io_bill` (类型=销售出库)。
       - 执行 `UPDATE wms_stock SET stock_qty = stock_qty - ?, locked_qty = locked_qty - ? ...`。
       - 记录 `wms_stock_log`。
    
    ### 第六阶段：财务核销 (Finance)
    
    **目标**：记录钱的流向。
    
    1. **收款/付款**:
       - 在采购/销售单详情页，点击“付款/收款”。
       - 插入 `fin_payment` 记录。
       - 更新 `fin_account` 余额。
       - 更新订单主表的 `pay_amount` / `receive_amount`。
    
    ## 4. 关键代码规范 (Developer Guidelines)
    
    ### 4.1 金额处理
    
    - **数据库**: `Decimal(16, 2)`
    - **Java**: 必须使用 `java.math.BigDecimal`。
    - **禁止**: 禁止使用 `Double` 进行金额计算。
      - ❌ `price * qty` (Double)
      - ✅ `price.multiply(qty)` (BigDecimal)
    
    ### 4.2 事务管理
    
    业务层涉及多表操作（尤其是库存变动）必须加 `@Transactional(rollbackFor = Exception.class)`。
    
    ### 4.3 异常处理
  
    以实际工程为准：当前统一由 `GlobalExceptionHandler` 将异常映射为稳定的 JSON 结构（包含 `message`）。

    - 业务错误：抛 `ResponseStatusException` 指定 HTTP 状态码（例如 400/404）
    - 参数校验失败：可抛 `IllegalArgumentException`（返回 400）
    - 并发冲突（乐观锁）：抛 `ObjectOptimisticLockingFailureException`（返回 409）

    例如库存不足时：
  
    ```
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品ID " + id + " 库存不足，当前可用: " + available);
    ```
    
    ### 4.4 审计字段填充
    
    以实际工程为准：目前 `create_time/update_time/create_by` 等字段由迁移脚本默认值/业务层显式赋值保障。
    （如需进一步工程化，可引入 Spring Data JPA Auditing 或 Hibernate `@CreationTimestamp/@UpdateTimestamp`。）
    
    5.库存核心逻辑实现
    
    ```java
    package com.ordererp.backend.wms.service;

    import com.ordererp.backend.wms.entity.WmsStock;
    import com.ordererp.backend.wms.repository.WmsStockRepository;
    import java.math.BigDecimal;
    import org.springframework.http.HttpStatus;
    import org.springframework.orm.ObjectOptimisticLockingFailureException;
    import org.springframework.stereotype.Service;
    import org.springframework.transaction.annotation.Transactional;
    import org.springframework.web.server.ResponseStatusException;

    /**
     * 库存核心逻辑演示（以当前工程实现为准：Spring Data JPA + @Version 乐观锁）
     * 包含：锁定库存、实际出库
     */
    @Service
    public class StockServiceDemo {

        private final WmsStockRepository stockRepository;

        public StockServiceDemo(WmsStockRepository stockRepository) {
            this.stockRepository = stockRepository;
        }

        /**
         * 场景：销售订单审核通过，预占库存（此时实物还在仓库，但不能再卖给别人）
         */
        @Transactional(rollbackFor = Exception.class)
        public void lockStock(Long warehouseId, Long productId, BigDecimal qty) {
            int attempts = 0;
            while (true) {
                attempts++;
                try {
                    doLockStockOnce(warehouseId, productId, qty);
                    return;
                } catch (ObjectOptimisticLockingFailureException e) {
                    if (attempts >= 3) throw e;
                }
            }
        }

        private void doLockStockOnce(Long warehouseId, Long productId, BigDecimal qty) {
            WmsStock stock = stockRepository.findFirstByWarehouseIdAndProductId(warehouseId, productId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "该仓库无此商品库存记录"));

            BigDecimal available = stock.getStockQty().subtract(stock.getLockedQty());
            if (available.compareTo(qty) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "库存不足！当前可用: " + available);
            }

            stock.setLockedQty(stock.getLockedQty().add(qty));
            stockRepository.saveAndFlush(stock);
        }

        /**
         * 场景：仓库发货，扣减物理库存并释放锁定库存
         */
        @Transactional(rollbackFor = Exception.class)
        public void deductStock(Long warehouseId, Long productId, BigDecimal qty) {
            WmsStock stock = stockRepository.findFirstByWarehouseIdAndProductId(warehouseId, productId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "该仓库无此商品库存记录"));

            // 校验逻辑略（可用库存、锁定库存等不变量校验）
            stock.setStockQty(stock.getStockQty().subtract(qty));
            stock.setLockedQty(stock.getLockedQty().subtract(qty));

            stockRepository.saveAndFlush(stock);

            // TODO: 在此处插入 wms_stock_log 库存流水/审计日志
        }
    }
    ```
  
