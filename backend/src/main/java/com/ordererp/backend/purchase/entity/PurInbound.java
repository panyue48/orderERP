package com.ordererp.backend.purchase.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "pur_inbound")
public class PurInbound {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "inbound_no", nullable = false, unique = true)
    private String inboundNo;

    @Column(name = "request_no", nullable = false, unique = true)
    private String requestNo;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "order_no", nullable = false)
    private String orderNo;

    @Column(name = "supplier_id", nullable = false)
    private Long supplierId;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    private Integer status;

    @Column(name = "qc_status")
    private Integer qcStatus;

    @Column(name = "qc_by")
    private String qcBy;

    @Column(name = "qc_time")
    private LocalDateTime qcTime;

    @Column(name = "qc_remark")
    private String qcRemark;

    @Column(name = "wms_bill_id")
    private Long wmsBillId;

    @Column(name = "wms_bill_no")
    private String wmsBillNo;

    @Column(name = "reverse_status")
    private Integer reverseStatus;

    @Column(name = "reverse_by")
    private String reverseBy;

    @Column(name = "reverse_time")
    private LocalDateTime reverseTime;

    @Column(name = "reverse_wms_bill_id")
    private Long reverseWmsBillId;

    @Column(name = "reverse_wms_bill_no")
    private String reverseWmsBillNo;

    private String remark;

    @Column(name = "create_by")
    private String createBy;

    @Column(name = "create_time")
    private LocalDateTime createTime;

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

    public String getInboundNo() {
        return inboundNo;
    }

    public void setInboundNo(String inboundNo) {
        this.inboundNo = inboundNo;
    }

    public String getRequestNo() {
        return requestNo;
    }

    public void setRequestNo(String requestNo) {
        this.requestNo = requestNo;
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

    public Long getSupplierId() {
        return supplierId;
    }

    public void setSupplierId(Long supplierId) {
        this.supplierId = supplierId;
    }

    public Long getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(Long warehouseId) {
        this.warehouseId = warehouseId;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getQcStatus() {
        return qcStatus;
    }

    public void setQcStatus(Integer qcStatus) {
        this.qcStatus = qcStatus;
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

    public String getQcRemark() {
        return qcRemark;
    }

    public void setQcRemark(String qcRemark) {
        this.qcRemark = qcRemark;
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

    public Integer getReverseStatus() {
        return reverseStatus;
    }

    public void setReverseStatus(Integer reverseStatus) {
        this.reverseStatus = reverseStatus;
    }

    public String getReverseBy() {
        return reverseBy;
    }

    public void setReverseBy(String reverseBy) {
        this.reverseBy = reverseBy;
    }

    public LocalDateTime getReverseTime() {
        return reverseTime;
    }

    public void setReverseTime(LocalDateTime reverseTime) {
        this.reverseTime = reverseTime;
    }

    public Long getReverseWmsBillId() {
        return reverseWmsBillId;
    }

    public void setReverseWmsBillId(Long reverseWmsBillId) {
        this.reverseWmsBillId = reverseWmsBillId;
    }

    public String getReverseWmsBillNo() {
        return reverseWmsBillNo;
    }

    public void setReverseWmsBillNo(String reverseWmsBillNo) {
        this.reverseWmsBillNo = reverseWmsBillNo;
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
