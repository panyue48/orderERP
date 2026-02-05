package com.ordererp.backend.purchase.repository;

import com.ordererp.backend.purchase.entity.PurApBill;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurApBillRepository extends JpaRepository<PurApBill, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from PurApBill b where b.id = :id")
    Optional<PurApBill> findByIdForUpdate(@Param("id") Long id);

    @Query(value = """
            select
              b.id as id,
              b.bill_no as billNo,
              b.supplier_id as supplierId,
              p.partner_code as supplierCode,
              p.partner_name as supplierName,
              b.start_date as startDate,
              b.end_date as endDate,
              b.total_amount as totalAmount,
              b.paid_amount as paidAmount,
              b.invoice_amount as invoiceAmount,
              b.status as status,
              b.remark as remark,
              b.create_by as createBy,
              b.create_time as createTime,
              b.audit_by as auditBy,
              b.audit_time as auditTime
            from pur_ap_bill b
            left join base_partner p on p.id = b.supplier_id
            where (:kw is null or :kw = ''
              or lower(b.bill_no) like lower(concat('%', :kw, '%'))
              or lower(p.partner_code) like lower(concat('%', :kw, '%'))
              or lower(p.partner_name) like lower(concat('%', :kw, '%')))
              and (:supplierId is null or b.supplier_id = :supplierId)
              and (:startDate is null or b.start_date >= :startDate)
              and (:endDate is null or b.end_date <= :endDate)
            order by b.id desc
            """,
            countQuery = """
            select count(*)
            from pur_ap_bill b
            left join base_partner p on p.id = b.supplier_id
            where (:kw is null or :kw = ''
              or lower(b.bill_no) like lower(concat('%', :kw, '%'))
              or lower(p.partner_code) like lower(concat('%', :kw, '%'))
              or lower(p.partner_name) like lower(concat('%', :kw, '%')))
              and (:supplierId is null or b.supplier_id = :supplierId)
              and (:startDate is null or b.start_date >= :startDate)
              and (:endDate is null or b.end_date <= :endDate)
            """,
            nativeQuery = true)
    Page<PurApBillRow> pageRows(@Param("kw") String keyword,
            @Param("supplierId") Long supplierId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);

    @Query(value = """
            select
              b.id as id,
              b.bill_no as billNo,
              b.supplier_id as supplierId,
              p.partner_code as supplierCode,
              p.partner_name as supplierName,
              b.start_date as startDate,
              b.end_date as endDate,
              b.total_amount as totalAmount,
              b.paid_amount as paidAmount,
              b.invoice_amount as invoiceAmount,
              b.status as status,
              b.remark as remark,
              b.create_by as createBy,
              b.create_time as createTime,
              b.audit_by as auditBy,
              b.audit_time as auditTime
            from pur_ap_bill b
            left join base_partner p on p.id = b.supplier_id
            where b.id = :id
            """, nativeQuery = true)
    PurApBillRow getRow(@Param("id") Long id);

    @Query(value = """
            select
              i.id as docId,
              i.inbound_no as docNo,
              i.order_id as orderId,
              i.order_no as orderNo,
              i.execute_time as docTime,
              coalesce(sum(d.plan_qty * od.price), 0.00) as amount
            from pur_inbound i
            join pur_inbound_detail d on d.inbound_id = i.id
            join pur_order_detail od on od.order_id = i.order_id and od.product_id = d.product_id
            left join pur_ap_doc_ref r on r.doc_type = 1 and r.doc_id = i.id
            where i.supplier_id = :supplierId
              and i.status = 2
              and i.qc_status = 2
              and i.execute_time is not null
              and i.execute_time >= :startTime
              and i.execute_time < :endTime
              and r.id is null
            group by i.id, i.inbound_no, i.order_id, i.order_no, i.execute_time
            order by i.execute_time asc, i.id asc
            """, nativeQuery = true)
    List<CandidateInboundRow> candidateInbounds(@Param("supplierId") Long supplierId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    @Query(value = """
            select
              r.id as docId,
              r.return_no as docNo,
              null as orderId,
              null as orderNo,
              r.execute_time as docTime,
              (0.00 - coalesce(r.total_amount, 0.00)) as amount
            from pur_return r
            left join pur_ap_doc_ref x on x.doc_type = 2 and x.doc_id = r.id
            where r.supplier_id = :supplierId
              and r.status = 4
              and r.execute_time is not null
              and r.execute_time >= :startTime
              and r.execute_time < :endTime
              and x.id is null
            order by r.execute_time asc, r.id asc
            """, nativeQuery = true)
    List<CandidateReturnRow> candidateReturns(@Param("supplierId") Long supplierId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    interface CandidateInboundRow {
        Long getDocId();

        String getDocNo();

        Long getOrderId();

        String getOrderNo();

        LocalDateTime getDocTime();

        BigDecimal getAmount();
    }

    interface CandidateReturnRow {
        Long getDocId();

        String getDocNo();

        Long getOrderId();

        String getOrderNo();

        LocalDateTime getDocTime();

        BigDecimal getAmount();
    }

    interface PurApBillRow {
        Long getId();

        String getBillNo();

        Long getSupplierId();

        String getSupplierCode();

        String getSupplierName();

        LocalDate getStartDate();

        LocalDate getEndDate();

        BigDecimal getTotalAmount();

        BigDecimal getPaidAmount();

        BigDecimal getInvoiceAmount();

        Integer getStatus();

        String getRemark();

        String getCreateBy();

        LocalDateTime getCreateTime();

        String getAuditBy();

        LocalDateTime getAuditTime();
    }
}
