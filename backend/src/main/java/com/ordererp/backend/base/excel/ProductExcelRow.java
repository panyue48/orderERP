package com.ordererp.backend.base.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import java.math.BigDecimal;

public class ProductExcelRow {
    @ExcelProperty("分类编码")
    private String categoryCode;

    @ExcelProperty("SKU")
    private String productCode;

    @ExcelProperty("商品名称")
    private String productName;

    @ExcelProperty("单位")
    private String unit;

    @ExcelProperty("参考进价")
    private BigDecimal purchasePrice;

    @ExcelProperty("标准售价")
    private BigDecimal salePrice;

    @ExcelProperty("库存预警")
    private Integer lowStock;

    @ExcelProperty("图片URL")
    private String imageUrl;

    @ExcelProperty("状态(1启用0停用)")
    private Integer status;

    public String getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(String categoryCode) {
        this.categoryCode = categoryCode;
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

    public BigDecimal getPurchasePrice() {
        return purchasePrice;
    }

    public void setPurchasePrice(BigDecimal purchasePrice) {
        this.purchasePrice = purchasePrice;
    }

    public BigDecimal getSalePrice() {
        return salePrice;
    }

    public void setSalePrice(BigDecimal salePrice) {
        this.salePrice = salePrice;
    }

    public Integer getLowStock() {
        return lowStock;
    }

    public void setLowStock(Integer lowStock) {
        this.lowStock = lowStock;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}

