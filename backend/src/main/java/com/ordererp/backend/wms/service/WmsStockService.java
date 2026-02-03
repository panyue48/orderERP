package com.ordererp.backend.wms.service;

import com.ordererp.backend.wms.dto.WmsStockResponse;
import com.ordererp.backend.wms.excel.WmsStockExcelRow;
import com.ordererp.backend.wms.repository.WmsStockRepository;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class WmsStockService {
    private final WmsStockRepository stockRepository;

    public WmsStockService(WmsStockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    public Page<WmsStockResponse> page(String keyword, Long warehouseId, Pageable pageable) {
        return stockRepository.pageRows(keyword, warehouseId, pageable).map(r -> {
            BigDecimal stockQty = r.getStockQty() == null ? BigDecimal.ZERO : r.getStockQty();
            BigDecimal lockedQty = r.getLockedQty() == null ? BigDecimal.ZERO : r.getLockedQty();
            BigDecimal available = stockQty.subtract(lockedQty);
            return new WmsStockResponse(
                    r.getId(),
                    r.getWarehouseId(),
                    r.getWarehouseName(),
                    r.getProductId(),
                    r.getProductCode(),
                    r.getProductName(),
                    r.getUnit(),
                    stockQty,
                    lockedQty,
                    available,
                    r.getUpdateTime());
        });
    }

    public List<WmsStockExcelRow> export(String keyword, Long warehouseId) {
        Page<WmsStockResponse> res = page(keyword, warehouseId, PageRequest.of(0, 50_000));
        return res.getContent().stream().map(this::toExcelRow).toList();
    }

    public WmsStockExcelRow toExcelRow(WmsStockResponse r) {
        WmsStockExcelRow row = new WmsStockExcelRow();
        row.setWarehouseName(r.warehouseName());
        row.setProductCode(r.productCode());
        row.setProductName(r.productName());
        row.setUnit(r.unit());
        row.setStockQty(r.stockQty());
        row.setLockedQty(r.lockedQty());
        row.setAvailableQty(r.availableQty());
        row.setUpdateTime(r.updateTime());
        return row;
    }
}
