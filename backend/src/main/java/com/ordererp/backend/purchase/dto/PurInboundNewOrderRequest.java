package com.ordererp.backend.purchase.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 在“采购入库单”页面发起：创建采购订单并立即生成/执行一张入库单（pur_inbound）。
 *
 * <p>requestNo 由客户端生成 UUID，用于幂等（重复提交不重复加库存）。</p>
 */
public record PurInboundNewOrderRequest(
        String requestNo,
        Long supplierId,
        LocalDate orderDate,
        Long warehouseId,
        String remark,
        List<PurInboundNewOrderLineRequest> lines) {
}

