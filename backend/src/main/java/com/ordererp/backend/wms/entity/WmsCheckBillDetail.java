package com.ordererp.backend.wms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "wms_check_bill_detail")
public class WmsCheckBillDetail {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bill_id", nullable = false)
    private Long billId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "counted_qty", nullable = false)
    private BigDecimal countedQty;

    @Column(name = "book_qty")
    private BigDecimal bookQty;

    @Column(name = "diff_qty")
    private BigDecimal diffQty;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBillId() {
        return billId;
    }

    public void setBillId(Long billId) {
        this.billId = billId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public BigDecimal getCountedQty() {
        return countedQty;
    }

    public void setCountedQty(BigDecimal countedQty) {
        this.countedQty = countedQty;
    }

    public BigDecimal getBookQty() {
        return bookQty;
    }

    public void setBookQty(BigDecimal bookQty) {
        this.bookQty = bookQty;
    }

    public BigDecimal getDiffQty() {
        return diffQty;
    }

    public void setDiffQty(BigDecimal diffQty) {
        this.diffQty = diffQty;
    }
}

