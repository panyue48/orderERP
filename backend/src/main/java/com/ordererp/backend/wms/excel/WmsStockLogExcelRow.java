package com.ordererp.backend.wms.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class WmsStockLogExcelRow {
    @ExcelProperty("CreateTime")
    private LocalDateTime createTime;

    @ExcelProperty("Warehouse")
    private String warehouseName;

    @ExcelProperty("SKU")
    private String productCode;

    @ExcelProperty("Product")
    private String productName;

    @ExcelProperty("BizType")
    private String bizType;

    @ExcelProperty("BizNo")
    private String bizNo;

    @ExcelProperty("ChangeQty")
    private BigDecimal changeQty;

    @ExcelProperty("AfterStockQty")
    private BigDecimal afterStockQty;

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

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

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public String getBizNo() {
        return bizNo;
    }

    public void setBizNo(String bizNo) {
        this.bizNo = bizNo;
    }

    public BigDecimal getChangeQty() {
        return changeQty;
    }

    public void setChangeQty(BigDecimal changeQty) {
        this.changeQty = changeQty;
    }

    public BigDecimal getAfterStockQty() {
        return afterStockQty;
    }

    public void setAfterStockQty(BigDecimal afterStockQty) {
        this.afterStockQty = afterStockQty;
    }
}

