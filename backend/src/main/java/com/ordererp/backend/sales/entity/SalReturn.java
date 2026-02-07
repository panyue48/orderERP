package com.ordererp.backend.sales.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "sal_return")
public class SalReturn {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "return_no", nullable = false, unique = true)
    private String returnNo;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "customer_code")
    private String customerCode;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "ship_id")
    private Long shipId;

    @Column(name = "ship_no")
    private String shipNo;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "order_no")
    private String orderNo;

    @Column(name = "return_date", nullable = false)
    private LocalDate returnDate;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    private Integer status;

    @Column(name = "wms_bill_id")
    private Long wmsBillId;

    @Column(name = "wms_bill_no")
    private String wmsBillNo;

    private String remark;

    @Column(name = "create_by")
    private String createBy;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "audit_by")
    private String auditBy;

    @Column(name = "audit_time")
    private LocalDateTime auditTime;

    @Column(name = "receive_by")
    private String receiveBy;

    @Column(name = "receive_time")
    private LocalDateTime receiveTime;

    @Column(name = "qc_by")
    private String qcBy;

    @Column(name = "qc_time")
    private LocalDateTime qcTime;

    @Column(name = "qc_disposition")
    private String qcDisposition;

    @Column(name = "qc_remark")
    private String qcRemark;

    @Column(name = "execute_by")
    private String executeBy;

    @Column(name = "execute_time")
    private LocalDateTime executeTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReturnNo() {
        return returnNo;
    }

    public void setReturnNo(String returnNo) {
        this.returnNo = returnNo;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getCustomerCode() {
        return customerCode;
    }

    public void setCustomerCode(String customerCode) {
        this.customerCode = customerCode;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public Long getShipId() {
        return shipId;
    }

    public void setShipId(Long shipId) {
        this.shipId = shipId;
    }

    public String getShipNo() {
        return shipNo;
    }

    public void setShipNo(String shipNo) {
        this.shipNo = shipNo;
    }

    public Long getOrderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public LocalDate getReturnDate() {
        return returnDate;
    }

    public void setReturnDate(LocalDate returnDate) {
        this.returnDate = returnDate;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Long getWmsBillId() {
        return wmsBillId;
    }

    public void setWmsBillId(Long wmsBillId) {
        this.wmsBillId = wmsBillId;
    }

    public String getWmsBillNo() {
        return wmsBillNo;
    }

    public void setWmsBillNo(String wmsBillNo) {
        this.wmsBillNo = wmsBillNo;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public String getAuditBy() {
        return auditBy;
    }

    public void setAuditBy(String auditBy) {
        this.auditBy = auditBy;
    }

    public LocalDateTime getAuditTime() {
        return auditTime;
    }

    public void setAuditTime(LocalDateTime auditTime) {
        this.auditTime = auditTime;
    }

    public String getReceiveBy() {
        return receiveBy;
    }

    public void setReceiveBy(String receiveBy) {
        this.receiveBy = receiveBy;
    }

    public LocalDateTime getReceiveTime() {
        return receiveTime;
    }

    public void setReceiveTime(LocalDateTime receiveTime) {
        this.receiveTime = receiveTime;
    }

    public String getQcBy() {
        return qcBy;
    }

    public void setQcBy(String qcBy) {
        this.qcBy = qcBy;
    }

    public LocalDateTime getQcTime() {
        return qcTime;
    }

    public void setQcTime(LocalDateTime qcTime) {
        this.qcTime = qcTime;
    }

    public String getQcDisposition() {
        return qcDisposition;
    }

    public void setQcDisposition(String qcDisposition) {
        this.qcDisposition = qcDisposition;
    }

    public String getQcRemark() {
        return qcRemark;
    }

    public void setQcRemark(String qcRemark) {
        this.qcRemark = qcRemark;
    }

    public String getExecuteBy() {
        return executeBy;
    }

    public void setExecuteBy(String executeBy) {
        this.executeBy = executeBy;
    }

    public LocalDateTime getExecuteTime() {
        return executeTime;
    }

    public void setExecuteTime(LocalDateTime executeTime) {
        this.executeTime = executeTime;
    }
}
