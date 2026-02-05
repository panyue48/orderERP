package com.ordererp.backend.purchase.repository;

import com.ordererp.backend.purchase.entity.PurApBillDetail;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
}
