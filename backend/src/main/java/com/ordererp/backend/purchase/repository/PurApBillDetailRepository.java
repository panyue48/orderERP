package com.ordererp.backend.purchase.repository;

import com.ordererp.backend.purchase.entity.PurApBillDetail;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurApBillDetailRepository extends JpaRepository<PurApBillDetail, Long> {
    List<PurApBillDetail> findByBillIdOrderByIdAsc(Long billId);

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
            from pur_ap_bill_detail d
            where d.bill_id = :billId
            order by d.id asc
            """, nativeQuery = true)
    List<PurApBillDetailRow> listRows(@Param("billId") Long billId);

    @Query(value = """
            select
              d.inbound_id as docId,
              d.product_code as productCode,
              d.product_name as productName,
              coalesce(sum(coalesce(d.real_qty, d.plan_qty)), 0.000) as qty
            from pur_inbound_detail d
            where d.inbound_id in (:docIds)
            group by d.inbound_id, d.product_code, d.product_name
            order by d.inbound_id asc, d.product_code asc
            """, nativeQuery = true)
    List<InboundDocItemRow> inboundDocItems(@Param("docIds") Collection<Long> docIds);

    @Query(value = """
            select
              d.return_id as docId,
              d.product_code as productCode,
              d.product_name as productName,
              coalesce(sum(d.qty), 0.000) as qty
            from pur_return_detail d
            where d.return_id in (:docIds)
            group by d.return_id, d.product_code, d.product_name
            order by d.return_id asc, d.product_code asc
            """, nativeQuery = true)
    List<ReturnDocItemRow> returnDocItems(@Param("docIds") Collection<Long> docIds);

    interface PurApBillDetailRow {
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

    interface InboundDocItemRow {
        Long getDocId();

        String getProductCode();

        String getProductName();

        BigDecimal getQty();
    }

    interface ReturnDocItemRow {
        Long getDocId();

        String getProductCode();

        String getProductName();

        BigDecimal getQty();
    }
}
