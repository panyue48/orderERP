package com.ordererp.backend.wms.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class WmsStockExcelRow {
    @ExcelProperty("Warehouse")
    private String warehouseName;

    @ExcelProperty("SKU")
    private String productCode;

    @ExcelProperty("Product")
    private String productName;

    @ExcelProperty("Unit")
    private String unit;

    @ExcelProperty("StockQty")
    private BigDecimal stockQty;

    @ExcelProperty("LockedQty")
    private BigDecimal lockedQty;

    @ExcelProperty("QcQty")
    private BigDecimal qcQty;

    @ExcelProperty("AvailableQty")
    private BigDecimal availableQty;

    @ExcelProperty("UpdateTime")
    private LocalDateTime updateTime;

    public String getWarehouseName() {
        return warehouseName;
    }

    public void setWarehouseName(String warehouseName) {
        this.warehouseName = warehouseName;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public BigDecimal getStockQty() {
        return stockQty;
    }

    public void setStockQty(BigDecimal stockQty) {
        this.stockQty = stockQty;
    }

    public BigDecimal getLockedQty() {
        return lockedQty;
    }

    public void setLockedQty(BigDecimal lockedQty) {
        this.lockedQty = lockedQty;
    }

    public BigDecimal getQcQty() {
        return qcQty;
    }

    public void setQcQty(BigDecimal qcQty) {
        this.qcQty = qcQty;
    }

    public BigDecimal getAvailableQty() {
        return availableQty;
    }

    public void setAvailableQty(BigDecimal availableQty) {
        this.availableQty = availableQty;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
