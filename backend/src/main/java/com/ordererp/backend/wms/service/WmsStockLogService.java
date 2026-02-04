package com.ordererp.backend.wms.service;

import com.ordererp.backend.wms.dto.WmsStockLogResponse;
import com.ordererp.backend.wms.excel.WmsStockLogExcelRow;
import com.ordererp.backend.wms.repository.WmsStockLogRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class WmsStockLogService {
    private final WmsStockLogRepository stockLogRepository;

    public WmsStockLogService(WmsStockLogRepository stockLogRepository) {
        this.stockLogRepository = stockLogRepository;
    }

    public Page<WmsStockLogResponse> page(String keyword, Long warehouseId, Long productId,
            LocalDateTime startTime, LocalDateTime endTime, Pageable pageable) {
        return stockLogRepository.pageRows(keyword, warehouseId, productId, startTime, endTime, pageable)
                .map(r -> new WmsStockLogResponse(
                r.getId(),
                r.getWarehouseId(),
                r.getWarehouseName(),
                r.getProductId(),
                r.getProductCode(),
                r.getProductName(),
                r.getBizType(),
                r.getBizNo(),
                r.getChangeQty(),
                r.getAfterStockQty(),
                r.getCreateTime()));
    }

    public List<WmsStockLogExcelRow> export(String keyword, Long warehouseId, Long productId,
            LocalDateTime startTime, LocalDateTime endTime) {
        Page<WmsStockLogResponse> res = page(keyword, warehouseId, productId, startTime, endTime,
                PageRequest.of(0, 50_000));
        return res.getContent().stream().map(WmsStockLogService::toExcelRow).toList();
    }

    public static WmsStockLogExcelRow toExcelRow(WmsStockLogResponse r) {
        // 导出 Excel 行：Controller 采用分页写出（流式）导出时，会按页把查询结果转换为 Excel 行。
        WmsStockLogExcelRow row = new WmsStockLogExcelRow();
        row.setCreateTime(r.createTime());
        row.setWarehouseName(r.warehouseName());
        row.setProductCode(r.productCode());
        row.setProductName(r.productName());
        row.setBizType(r.bizType());
        row.setBizNo(r.bizNo());
        row.setChangeQty(r.changeQty());
        row.setAfterStockQty(r.afterStockQty());
        return row;
    }
}
