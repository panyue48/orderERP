package com.ordererp.backend.base.excel;

import com.alibaba.excel.annotation.ExcelProperty;

public class CategoryExcelRow {
    @ExcelProperty("父分类编码")
    private String parentCategoryCode;

    @ExcelProperty("分类编码")
    private String categoryCode;

    @ExcelProperty("分类名称")
    private String categoryName;

    @ExcelProperty("排序")
    private Integer sort;

    @ExcelProperty("状态(1启用0停用)")
    private Integer status;

    public String getParentCategoryCode() {
        return parentCategoryCode;
    }

    public void setParentCategoryCode(String parentCategoryCode) {
        this.parentCategoryCode = parentCategoryCode;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(String categoryCode) {
        this.categoryCode = categoryCode;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}

