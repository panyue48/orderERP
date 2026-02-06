package com.ordererp.backend.sales.repository;

import com.ordererp.backend.sales.entity.SalArBillDetail;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalArBillDetailRepository extends JpaRepository<SalArBillDetail, Long> {
    long deleteByBillId(Long billId);

    @Query(value = """
            select
              d.id as id,
              d.bill_id as billId,
              d.doc_type as docType,
              d.doc_id as docId,
              d.doc_no as docNo,
              d.order_id as orderId,
              d.order_no as orderNo,
              d.doc_time as docTime,
              d.amount as amount
            from sal_ar_bill_detail d
            where d.bill_id = :billId
            order by d.id asc
            """, nativeQuery = true)
    List<SalArBillDetailRow> listRows(@Param("billId") Long billId);

    @Query(value = """
            select
              d.ship_id as docId,
              d.product_code as productCode,
              d.product_name as productName,
              coalesce(sum(d.qty), 0.000) as qty
            from sal_ship_detail d
            where d.ship_id in (:docIds)
            group by d.ship_id, d.product_code, d.product_name
            order by d.ship_id asc, d.product_code asc
            """, nativeQuery = true)
    List<ShipDocItemRow> shipDocItems(@Param("docIds") Collection<Long> docIds);

    @Query(value = """
            select
              d.return_id as docId,
              d.product_code as productCode,
              d.product_name as productName,
              coalesce(sum(d.qty), 0.000) as qty
            from sal_return_detail d
            where d.return_id in (:docIds)
            group by d.return_id, d.product_code, d.product_name
            order by d.return_id asc, d.product_code asc
            """, nativeQuery = true)
    List<ReturnDocItemRow> returnDocItems(@Param("docIds") Collection<Long> docIds);

    interface SalArBillDetailRow {
        Long getId();

        Long getBillId();

        Integer getDocType();

        Long getDocId();

        String getDocNo();

        Long getOrderId();

        String getOrderNo();

        LocalDateTime getDocTime();

        BigDecimal getAmount();
    }

    interface ShipDocItemRow {
        Long getDocId();

        String getProductCode();

        String getProductName();

        BigDecimal getQty();
    }

    interface ReturnDocItemRow extends ShipDocItemRow {
    }
}

