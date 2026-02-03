package com.ordererp.backend.base.excel;

import com.alibaba.excel.annotation.ExcelProperty;
import java.math.BigDecimal;

public class PartnerExcelRow {
    @ExcelProperty("单位编码")
    private String partnerCode;

    @ExcelProperty("单位名称")
    private String partnerName;

    @ExcelProperty("类型(1供应商2客户)")
    private Integer type;

    @ExcelProperty("联系人")
    private String contact;

    @ExcelProperty("电话")
    private String phone;

    @ExcelProperty("邮箱")
    private String email;

    @ExcelProperty("信用额度")
    private BigDecimal creditLimit;

    @ExcelProperty("状态(1启用0停用)")
    private Integer status;

    public String getPartnerCode() {
        return partnerCode;
    }

    public void setPartnerCode(String partnerCode) {
        this.partnerCode = partnerCode;
    }

    public String getPartnerName() {
        return partnerName;
    }

    public void setPartnerName(String partnerName) {
        this.partnerName = partnerName;
    }

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public String getContact() {
        return contact;
    }

    public void setContact(String contact) {
        this.contact = contact;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public BigDecimal getCreditLimit() {
        return creditLimit;
    }

    public void setCreditLimit(BigDecimal creditLimit) {
        this.creditLimit = creditLimit;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }
}

