package com.ordererp.backend.purchase.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import java.math.BigDecimal;
import java.time.LocalDate;

public class PurOrderExcelRow {
    @ExcelProperty("采购单号")
    private String orderNo;

    @ExcelProperty("供应商编码")
    private String supplierCode;

    @ExcelProperty("供应商名称")
    private String supplierName;

    @ExcelProperty("日期")
    private LocalDate orderDate;

    @ExcelProperty("状态")
    private Integer status;

    @ExcelProperty("SKU")
    private String productCode;

    @ExcelProperty("商品名称")
    private String productName;

    @ExcelProperty("单位")
    private String unit;

    @ExcelProperty("单价")
    private BigDecimal price;

    @ExcelProperty("采购数量")
    private BigDecimal qty;

    @ExcelProperty("金额")
    private BigDecimal amount;

    @ExcelProperty("已入库")
    private BigDecimal inQty;

    @ExcelProperty("备注")
    private String remark;

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getSupplierCode() {
        return supplierCode;
    }

    public void setSupplierCode(String supplierCode) {
        this.supplierCode = supplierCode;
    }

    public String getSupplierName() {
        return supplierName;
    }

    public void setSupplierName(String supplierName) {
        this.supplierName = supplierName;
    }

    public LocalDate getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(LocalDate orderDate) {
        this.orderDate = orderDate;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
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

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getQty() {
        return qty;
    }

    public void setQty(BigDecimal qty) {
        this.qty = qty;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getInQty() {
        return inQty;
    }

    public void setInQty(BigDecimal inQty) {
        this.inQty = inQty;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}

