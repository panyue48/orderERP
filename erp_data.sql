/*
 * ERP 企业资源计划系统 - 终极完整版数据库脚本 (v4.0)
 * 技术栈适配: Vue3 + SpringBoot + MyBatis-Plus
 * * 包含模块:
 * 1. 系统管理 (SYS): 用户、角色、菜单、权限分配 (RBAC模型)
 * 2. 基础资料 (BASE): 商品、仓库、往来单位
 * 3. 采购管理 (PUR): 采购订单
 * 4. 销售管理 (SAL): 销售订单
 * 5. 库存管理 (WMS): 实时库存、出入库单、库存流水
 * 6. 财务管理 (FIN): 资金账户、收付款流水
 */

-- ==========================================
-- 0. 环境初始化
-- ==========================================
CREATE DATABASE IF NOT EXISTS `erp_data` CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `erp_data`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ==========================================
-- 1. 系统管理模块 (System & RBAC)
-- ==========================================

-- [1.1] 系统用户表
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(50) NOT NULL COMMENT '登录账号',
  `password` varchar(100) NOT NULL COMMENT '登录密码 (BCrypt加密)',
  `nickname` varchar(50) DEFAULT NULL COMMENT '用户昵称',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像地址',
  `status` tinyint(1) DEFAULT 1 COMMENT '状态: 1启用 0禁用',
  `deleted` tinyint(1) DEFAULT 0 COMMENT '逻辑删除: 1已删 0未删',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- [1.2] 系统角色表
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `role_name` varchar(50) NOT NULL COMMENT '角色名称 (如: 销售经理)',
  `role_key` varchar(50) NOT NULL COMMENT '角色权限字符 (如: sale_mgr)',
  `sort` int(11) DEFAULT 0 COMMENT '显示顺序',
  `status` tinyint(1) DEFAULT 1 COMMENT '状态: 1启用 0禁用',
  `deleted` tinyint(1) DEFAULT 0,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_key` (`role_key`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- [1.3] 菜单权限表
DROP TABLE IF EXISTS `sys_menu`;
CREATE TABLE `sys_menu` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `parent_id` bigint(20) DEFAULT 0 COMMENT '父菜单ID',
  `menu_name` varchar(50) NOT NULL COMMENT '菜单名称',
  `path` varchar(200) DEFAULT NULL COMMENT '路由地址',
  `component` varchar(255) DEFAULT NULL COMMENT 'Vue组件路径',
  `perms` varchar(100) DEFAULT NULL COMMENT '权限标识 (sys:user:add)',
  `icon` varchar(100) DEFAULT NULL COMMENT '菜单图标',
  `menu_type` char(1) DEFAULT 'M' COMMENT '类型: M目录 C菜单 F按钮',
  `sort` int(11) DEFAULT 0 COMMENT '排序',
  `visible` tinyint(1) DEFAULT 1 COMMENT '显示状态',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=1000 DEFAULT CHARSET=utf8mb4 COMMENT='菜单权限表';

-- [1.4] 用户-角色关联表 (N:N)
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role` (
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `role_id` bigint(20) NOT NULL COMMENT '角色ID',
  PRIMARY KEY (`user_id`, `role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联';

-- [1.5] 角色-菜单关联表 (N:N)
DROP TABLE IF EXISTS `sys_role_menu`;
CREATE TABLE `sys_role_menu` (
  `role_id` bigint(20) NOT NULL COMMENT '角色ID',
  `menu_id` bigint(20) NOT NULL COMMENT '菜单ID',
  PRIMARY KEY (`role_id`, `menu_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单关联';


-- ==========================================
-- 2. 基础资料模块 (Base Data)
-- ==========================================

-- [2.1] 商品表
DROP TABLE IF EXISTS `base_product`;
CREATE TABLE `base_product` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `category_id` bigint(20) DEFAULT NULL COMMENT '分类ID',
  `product_code` varchar(64) NOT NULL COMMENT '商品编码(SKU)',
  `product_name` varchar(128) NOT NULL COMMENT '商品名称',
  `barcode` varchar(64) DEFAULT NULL COMMENT '条形码',
  `spec` varchar(128) DEFAULT NULL COMMENT '规格型号',
  `unit` varchar(32) DEFAULT '个' COMMENT '计量单位',
  `weight` decimal(10,3) DEFAULT 0.000 COMMENT '重量(kg)',
  `purchase_price` decimal(16,2) DEFAULT 0.00 COMMENT '参考进价',
  `sale_price` decimal(16,2) DEFAULT 0.00 COMMENT '标准售价',
  `low_stock` int(11) DEFAULT 10 COMMENT '库存下限预警',
  `status` tinyint(1) DEFAULT 1 COMMENT '状态: 1启用 0停用',
  `deleted` tinyint(1) DEFAULT 0,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`product_code`)
) ENGINE=InnoDB AUTO_INCREMENT=10000 DEFAULT CHARSET=utf8mb4 COMMENT='商品基础信息表';

-- [2.2] 仓库表
DROP TABLE IF EXISTS `base_warehouse`;
CREATE TABLE `base_warehouse` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `warehouse_code` varchar(32) NOT NULL COMMENT '仓库编码',
  `warehouse_name` varchar(64) NOT NULL COMMENT '仓库名称',
  `location` varchar(255) DEFAULT NULL COMMENT '仓库地址',
  `manager` varchar(32) DEFAULT NULL COMMENT '负责人',
  `status` tinyint(1) DEFAULT 1,
  `deleted` tinyint(1) DEFAULT 0,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COMMENT='仓库表';

-- [2.3] 往来单位表 (供应商/客户)
DROP TABLE IF EXISTS `base_partner`;
CREATE TABLE `base_partner` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `partner_name` varchar(128) NOT NULL COMMENT '单位名称',
  `partner_code` varchar(64) NOT NULL COMMENT '单位编码',
  `type` tinyint(1) NOT NULL COMMENT '类型: 1供应商 2客户',
  `contact` varchar(64) DEFAULT NULL COMMENT '联系人',
  `phone` varchar(32) DEFAULT NULL COMMENT '电话',
  `email` varchar(64) DEFAULT NULL,
  `credit_limit` decimal(16,2) DEFAULT 0.00 COMMENT '信用额度',
  `status` tinyint(1) DEFAULT 1,
  `deleted` tinyint(1) DEFAULT 0,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2000 DEFAULT CHARSET=utf8mb4 COMMENT='往来单位表';


-- ==========================================
-- 3. 采购管理模块 (Purchase)
-- ==========================================

-- [3.1] 采购订单主表
DROP TABLE IF EXISTS `pur_order`;
CREATE TABLE `pur_order` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL COMMENT '采购单号 (PO+日期+流水)',
  `supplier_id` bigint(20) NOT NULL COMMENT '供应商ID',
  `order_date` date NOT NULL COMMENT '单据日期',
  `total_amount` decimal(16,2) NOT NULL DEFAULT 0.00 COMMENT '订单总额',
  `pay_amount` decimal(16,2) DEFAULT 0.00 COMMENT '已付金额',
  `status` tinyint(1) DEFAULT 1 COMMENT '状态: 1待审核 2已审核 3部分入库 4已完成 9已作废',
  `remark` varchar(512) DEFAULT NULL,
  `create_by` varchar(64) DEFAULT NULL COMMENT '制单人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `audit_by` varchar(64) DEFAULT NULL COMMENT '审核人',
  `audit_time` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`)
) ENGINE=InnoDB AUTO_INCREMENT=3000 DEFAULT CHARSET=utf8mb4 COMMENT='采购订单主表';

-- [3.2] 采购订单明细表
DROP TABLE IF EXISTS `pur_order_detail`;
CREATE TABLE `pur_order_detail` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `order_id` bigint(20) NOT NULL COMMENT '主表ID',
  `product_id` bigint(20) NOT NULL COMMENT '商品ID',
  -- 快照字段 (防止商品修改影响历史单据)
  `product_code` varchar(64) DEFAULT NULL,
  `product_name` varchar(128) DEFAULT NULL,
  `unit` varchar(32) DEFAULT NULL,
  
  `price` decimal(16,2) NOT NULL DEFAULT 0.00 COMMENT '采购单价',
  `qty` decimal(16,3) NOT NULL DEFAULT 0.000 COMMENT '采购数量',
  `amount` decimal(16,2) NOT NULL DEFAULT 0.00 COMMENT '金额 (price*qty)',
  `in_qty` decimal(16,3) DEFAULT 0.000 COMMENT '已入库数量 (用于跟踪进度)',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='采购订单明细表';


-- ==========================================
-- 4. 销售管理模块 (Sales)
-- ==========================================

-- [4.1] 销售订单主表
DROP TABLE IF EXISTS `sal_order`;
CREATE TABLE `sal_order` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `order_no` varchar(64) NOT NULL COMMENT '销售单号 (SO+日期+流水)',
  `customer_id` bigint(20) NOT NULL COMMENT '客户ID',
  `order_date` date NOT NULL COMMENT '单据日期',
  `total_amount` decimal(16,2) NOT NULL DEFAULT 0.00 COMMENT '订单总额',
  `discount_amount` decimal(16,2) DEFAULT 0.00 COMMENT '优惠金额',
  `real_amount` decimal(16,2) NOT NULL DEFAULT 0.00 COMMENT '应收金额',
  `receive_amount` decimal(16,2) DEFAULT 0.00 COMMENT '已收金额',
  
  `audit_status` tinyint(1) DEFAULT 0 COMMENT '审核状态: 0待审 1已审',
  `stock_status` tinyint(1) DEFAULT 0 COMMENT '出库状态: 0未出 1部分 2全部',
  `pay_status` tinyint(1) DEFAULT 0 COMMENT '收款状态: 0未收 1部分 2全部',
  
  `remark` varchar(512) DEFAULT NULL,
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`)
) ENGINE=InnoDB AUTO_INCREMENT=4000 DEFAULT CHARSET=utf8mb4 COMMENT='销售订单主表';

-- [4.2] 销售订单明细表
DROP TABLE IF EXISTS `sal_order_detail`;
CREATE TABLE `sal_order_detail` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `order_id` bigint(20) NOT NULL COMMENT '主表ID',
  `product_id` bigint(20) NOT NULL COMMENT '商品ID',
  -- 快照字段
  `product_code` varchar(64) DEFAULT NULL,
  `product_name` varchar(128) DEFAULT NULL,
  `unit` varchar(32) DEFAULT NULL,
  
  `price` decimal(16,2) NOT NULL DEFAULT 0.00 COMMENT '销售单价',
  `qty` decimal(16,3) NOT NULL DEFAULT 0.000 COMMENT '销售数量',
  `amount` decimal(16,2) NOT NULL DEFAULT 0.00 COMMENT '金额',
  `out_qty` decimal(16,3) DEFAULT 0.000 COMMENT '已发货数量',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='销售订单明细表';


-- ==========================================
-- 5. 库存管理模块 (WMS)
-- ==========================================

-- [5.1] 实时库存表 (核心表)
DROP TABLE IF EXISTS `wms_stock`;
CREATE TABLE `wms_stock` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `warehouse_id` bigint(20) NOT NULL COMMENT '仓库ID',
  `product_id` bigint(20) NOT NULL COMMENT '商品ID',
  `stock_qty` decimal(16,3) NOT NULL DEFAULT 0.000 COMMENT '物理库存 (仓库里实际有的)',
  `locked_qty` decimal(16,3) NOT NULL DEFAULT 0.000 COMMENT '锁定库存 (已下单但未发货)',
  `version` int(11) DEFAULT 0 COMMENT '乐观锁版本号 (MyBatis-Plus插件支持)',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wh_prod` (`warehouse_id`, `product_id`) COMMENT '一个仓库一个商品只有一条记录'
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COMMENT='实时库存表';

-- [5.2] 出入库单据 (控制库存变动的唯一入口)
DROP TABLE IF EXISTS `wms_io_bill`;
CREATE TABLE `wms_io_bill` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `bill_no` varchar(64) NOT NULL COMMENT '单据编号',
  `type` tinyint(1) NOT NULL COMMENT '类型: 1采购入库 2销售出库 3盘点入库 4盘点出库',
  `biz_id` bigint(20) DEFAULT NULL COMMENT '关联的业务单ID (采购单ID/销售单ID)',
  `biz_no` varchar(64) DEFAULT NULL COMMENT '关联业务单号',
  `warehouse_id` bigint(20) NOT NULL COMMENT '发生仓库ID',
  `status` tinyint(1) DEFAULT 1 COMMENT '1待执行 2已完成',
  `remark` varchar(255) DEFAULT NULL,
  `create_by` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='出入库单主表';

-- [5.3] 出入库明细
DROP TABLE IF EXISTS `wms_io_bill_detail`;
CREATE TABLE `wms_io_bill_detail` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `bill_id` bigint(20) NOT NULL,
  `product_id` bigint(20) NOT NULL,
  `qty` decimal(16,3) NOT NULL COMMENT '应收/应发数量',
  `real_qty` decimal(16,3) DEFAULT 0.000 COMMENT '实收/实发数量',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='出入库单明细';

-- [5.4] 库存变动流水 (审计日志)
DROP TABLE IF EXISTS `wms_stock_log`;
CREATE TABLE `wms_stock_log` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `warehouse_id` bigint(20) NOT NULL,
  `product_id` bigint(20) NOT NULL,
  `biz_type` varchar(32) NOT NULL COMMENT '业务类型(采购入库/销售出库)',
  `biz_no` varchar(64) DEFAULT NULL COMMENT '关联单号',
  `change_qty` decimal(16,3) NOT NULL COMMENT '变动数量 (+/-)',
  `after_stock_qty` decimal(16,3) NOT NULL COMMENT '变动后物理库存',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='库存变动流水表';


-- ==========================================
-- 6. 财务管理模块 (Finance)
-- ==========================================

-- [6.1] 资金账户表
DROP TABLE IF EXISTS `fin_account`;
CREATE TABLE `fin_account` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `account_name` varchar(64) NOT NULL COMMENT '账户名称',
  `account_no` varchar(64) DEFAULT NULL COMMENT '银行账号/支付宝号',
  `balance` decimal(16,2) DEFAULT 0.00 COMMENT '当前余额',
  `remark` varchar(255) DEFAULT NULL,
  `status` tinyint(1) DEFAULT 1,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COMMENT='资金账户表';

-- [6.2] 收付款记录表 (核销记录)
DROP TABLE IF EXISTS `fin_payment`;
CREATE TABLE `fin_payment` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `pay_no` varchar(64) NOT NULL COMMENT '流水号',
  `type` tinyint(1) NOT NULL COMMENT '1收款 2付款',
  `partner_id` bigint(20) NOT NULL COMMENT '往来单位ID',
  `account_id` bigint(20) NOT NULL COMMENT '收付款账户ID',
  `amount` decimal(16,2) NOT NULL COMMENT '交易金额',
  `biz_no` varchar(64) DEFAULT NULL COMMENT '关联订单号(选填)',
  `pay_date` date NOT NULL COMMENT '交易日期',
  `create_by` varchar(64) DEFAULT NULL COMMENT '操作人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收付款记录表';


-- ==========================================
-- 7. 初始化模拟数据 (Mock Data)
-- ==========================================

-- 1. 用户: admin / 123456
INSERT INTO `sys_user` (`id`, `username`, `password`, `nickname`, `status`) VALUES 
(1, 'admin', '123456', '系统管理员', 1),
(2, 'manager', '123456', '张经理', 1),
(3, 'staff', '123456', '李员工', 1);

-- 2. 角色
INSERT INTO `sys_role` (`id`, `role_name`, `role_key`) VALUES 
(1, '超级管理员', 'admin'),
(2, '销售经理', 'sale_mgr');

-- 3. 关联: admin是超级管理员
INSERT INTO `sys_user_role` VALUES (1, 1);

-- 4. 菜单: 简单插入几个
INSERT INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `path`, `menu_type`, `visible`) VALUES
(1, 0, '系统管理', '/system', 'M', 1),
(2, 1, '用户管理', '/system/user', 'C', 1),
(3, 1, '角色管理', '/system/role', 'C', 1),
(4, 0, '基础资料', '/base', 'M', 1),
(5, 4, '商品管理', '/base/product', 'C', 1);

-- 5. 权限: 超级管理员拥有所有权限 (模拟)
INSERT INTO `sys_role_menu` VALUES (1, 1), (1, 2), (1, 3), (1, 4), (1, 5);

-- 6. 仓库 & 账户
INSERT INTO `base_warehouse` (`id`, `warehouse_code`, `warehouse_name`) VALUES (1, 'WH-01', '主营仓库');
INSERT INTO `fin_account` (`id`, `account_name`, `balance`) VALUES (1, '公司基本户', 1000000.00);

-- 7. 客户 & 供应商
INSERT INTO `base_partner` (`id`, `partner_name`, `partner_code`, `type`) VALUES 
(1, '联想电脑供应商', 'SUP-001', 1),
(2, '大客户-腾讯', 'CUS-001', 2);

-- 8. 商品
INSERT INTO `base_product` (`id`, `product_code`, `product_name`, `unit`, `purchase_price`, `sale_price`) VALUES
(1, 'PROD001', 'ThinkPad X1 Carbon', '台', 8000.00, 12000.00);

-- 9. 初始库存 (通过 SQL 直接注入，实际应通过入库单)
INSERT INTO `wms_stock` (`warehouse_id`, `product_id`, `stock_qty`, `version`) VALUES (1, 1, 50, 0);

SET FOREIGN_KEY_CHECKS = 1;