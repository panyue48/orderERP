package com.ordererp.backend.sales.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "sal_ship")
public class SalShip {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ship_no", nullable = false)
    private String shipNo;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "order_no")
    private String orderNo;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "customer_code")
    private String customerCode;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "warehouse_id", nullable = false)
    private Long warehouseId;

    @Column(name = "ship_time")
    private LocalDateTime shipTime;

    @Column(name = "total_qty", nullable = false)
    private BigDecimal totalQty;

    @Column(name = "wms_bill_id")
    private Long wmsBillId;

    @Column(name = "wms_bill_no")
    private String wmsBillNo;

    @Column(name = "create_by")
    private String createBy;

    @Column(name = "create_time")
    private LocalDateTime createTime;

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

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public LocalDateTime getShipTime() {
        return shipTime;
    }

    public void setShipTime(LocalDateTime shipTime) {
        this.shipTime = shipTime;
    }

    public BigDecimal getTotalQty() {
        return totalQty;
    }

    public void setTotalQty(BigDecimal totalQty) {
        this.totalQty = totalQty;
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
}
