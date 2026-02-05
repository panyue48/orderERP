package com.ordererp.backend.purchase.dto;

import java.math.BigDecimal;

/**
 * 新建采购入库（同时新建采购订单）行明细：
 * - qty：采购数量（计划/订单数量）
 * - inboundQty：本次入库数量（允许小于 qty，用于分批入库）
 */
public record PurInboundNewOrderLineRequest(Long productId, BigDecimal price, BigDecimal qty, BigDecimal inboundQty) {
}

