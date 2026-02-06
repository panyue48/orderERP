package com.ordererp.backend.sales.repository;

import com.ordererp.backend.sales.entity.SalArReceipt;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalArReceiptRepository extends JpaRepository<SalArReceipt, Long> {
    @Query(value = """
            select
              p.id as id,
              p.receipt_no as receiptNo,
              p.bill_id as billId,
              p.customer_id as customerId,
              p.receipt_date as receiptDate,
              p.amount as amount,
              p.method as method,
              p.remark as remark,
              p.status as status,
              p.create_by as createBy,
              p.create_time as createTime,
              p.cancel_by as cancelBy,
              p.cancel_time as cancelTime
            from sal_ar_receipt p
            where p.bill_id = :billId
            order by p.id asc
            """, nativeQuery = true)
    List<SalArReceiptRow> listRows(@Param("billId") Long billId);

    @Query(value = """
            select coalesce(sum(p.amount), 0.00)
            from sal_ar_receipt p
            where p.bill_id = :billId
              and p.status = 2
            """, nativeQuery = true)
    BigDecimal sumCompletedAmount(@Param("billId") Long billId);

    interface SalArReceiptRow {
        Long getId();

        String getReceiptNo();

        Long getBillId();

        Long getCustomerId();

        LocalDate getReceiptDate();

        BigDecimal getAmount();

        String getMethod();

        String getRemark();

        Integer getStatus();

        String getCreateBy();

        LocalDateTime getCreateTime();

        String getCancelBy();

        LocalDateTime getCancelTime();
    }
}

