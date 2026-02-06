package com.ordererp.backend.sales.repository;

import com.ordererp.backend.sales.entity.SalArBill;
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

public interface SalArBillRepository extends JpaRepository<SalArBill, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from SalArBill b where b.id = :id")
    Optional<SalArBill> findByIdForUpdate(@Param("id") Long id);

    @Query(value = """
            select
              b.id as id,
              b.bill_no as billNo,
              b.customer_id as customerId,
              p.partner_code as customerCode,
              p.partner_name as customerName,
              b.start_date as startDate,
              b.end_date as endDate,
              b.total_amount as totalAmount,
              b.received_amount as receivedAmount,
              b.invoice_amount as invoiceAmount,
              b.status as status,
              b.remark as remark,
              b.create_by as createBy,
              b.create_time as createTime,
              b.audit_by as auditBy,
              b.audit_time as auditTime
            from sal_ar_bill b
            left join base_partner p on p.id = b.customer_id
            where (:kw is null or :kw = ''
              or lower(b.bill_no) like lower(concat('%', :kw, '%'))
              or lower(p.partner_code) like lower(concat('%', :kw, '%'))
              or lower(p.partner_name) like lower(concat('%', :kw, '%')))
              and (:customerId is null or b.customer_id = :customerId)
              and (:startDate is null or b.start_date >= :startDate)
              and (:endDate is null or b.end_date <= :endDate)
            order by b.id desc
            """,
            countQuery = """
            select count(*)
            from sal_ar_bill b
            left join base_partner p on p.id = b.customer_id
            where (:kw is null or :kw = ''
              or lower(b.bill_no) like lower(concat('%', :kw, '%'))
              or lower(p.partner_code) like lower(concat('%', :kw, '%'))
              or lower(p.partner_name) like lower(concat('%', :kw, '%')))
              and (:customerId is null or b.customer_id = :customerId)
              and (:startDate is null or b.start_date >= :startDate)
              and (:endDate is null or b.end_date <= :endDate)
            """,
            nativeQuery = true)
    Page<SalArBillRow> pageRows(@Param("kw") String keyword,
            @Param("customerId") Long customerId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);

    @Query(value = """
            select
              b.id as id,
              b.bill_no as billNo,
              b.customer_id as customerId,
              p.partner_code as customerCode,
              p.partner_name as customerName,
              b.start_date as startDate,
              b.end_date as endDate,
              b.total_amount as totalAmount,
              b.received_amount as receivedAmount,
              b.invoice_amount as invoiceAmount,
              b.status as status,
              b.remark as remark,
              b.create_by as createBy,
              b.create_time as createTime,
              b.audit_by as auditBy,
              b.audit_time as auditTime
            from sal_ar_bill b
            left join base_partner p on p.id = b.customer_id
            where b.id = :id
            """, nativeQuery = true)
    SalArBillRow getRow(@Param("id") Long id);

    @Query(value = """
            select
              s.id as docId,
              s.ship_no as docNo,
              s.order_id as orderId,
              s.order_no as orderNo,
              s.ship_time as docTime,
              coalesce(sum(d.qty * coalesce(od.price, 0.00)), 0.00) as amount
            from sal_ship s
            join sal_ship_detail d on d.ship_id = s.id
            join sal_order_detail od on od.id = d.order_detail_id
            left join sal_ar_doc_ref r on r.doc_type = 1 and r.doc_id = s.id
            where s.customer_id = :customerId
              and (s.reverse_status is null or s.reverse_status = 0)
              and s.ship_time is not null
              and s.ship_time >= :startTime
              and s.ship_time < :endTime
              and r.id is null
            group by s.id, s.ship_no, s.order_id, s.order_no, s.ship_time
            order by s.ship_time asc, s.id asc
            """, nativeQuery = true)
    List<CandidateShipRow> candidateShips(@Param("customerId") Long customerId,
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
            from sal_return r
            left join sal_ar_doc_ref x on x.doc_type = 2 and x.doc_id = r.id
            where r.customer_id = :customerId
              and r.status = 4
              and r.execute_time is not null
              and r.execute_time >= :startTime
              and r.execute_time < :endTime
              and x.id is null
            order by r.execute_time asc, r.id asc
            """, nativeQuery = true)
    List<CandidateReturnRow> candidateReturns(@Param("customerId") Long customerId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);

    interface CandidateShipRow {
        Long getDocId();

        String getDocNo();

        Long getOrderId();

        String getOrderNo();

        LocalDateTime getDocTime();

        BigDecimal getAmount();
    }

    interface CandidateReturnRow extends CandidateShipRow {
    }

    interface SalArBillRow {
        Long getId();

        String getBillNo();

        Long getCustomerId();

        String getCustomerCode();

        String getCustomerName();

        LocalDate getStartDate();

        LocalDate getEndDate();

        BigDecimal getTotalAmount();

        BigDecimal getReceivedAmount();

        BigDecimal getInvoiceAmount();

        Integer getStatus();

        String getRemark();

        String getCreateBy();

        LocalDateTime getCreateTime();

        String getAuditBy();

        LocalDateTime getAuditTime();
    }
}

