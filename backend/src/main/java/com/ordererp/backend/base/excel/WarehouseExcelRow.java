package com.ordererp.backend.base.excel;

import com.alibaba.excel.annotation.ExcelProperty;

public class WarehouseExcelRow {
    @ExcelProperty("仓库编码")
    private String warehouseCode;

    @ExcelProperty("仓库名称")
    private String warehouseName;

    @ExcelProperty("地址")
    private String location;

    @ExcelProperty("负责人")
    private String manager;

    @ExcelProperty("状态(1启用0停用)")
    private Integer status;

    public String getWarehouseCode() {
        return warehouseCode;
    }

    public void setWarehouseCode(String warehouseCode) {
        this.warehouseCode = warehouseCode;
    }

    public String getWarehouseName() {
        return warehouseName;
    }

    public void setWarehouseName(String warehouseName) {
        this.warehouseName = warehouseName;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getManager() {
        return manager;
    }

    public void setManager(String manager) {
        this.manager = manager;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}

