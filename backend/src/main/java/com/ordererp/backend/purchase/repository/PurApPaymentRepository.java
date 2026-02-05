package com.ordererp.backend.purchase.repository;

import com.ordererp.backend.purchase.entity.PurApPayment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurApPaymentRepository extends JpaRepository<PurApPayment, Long> {
    List<PurApPayment> findByBillIdOrderByIdAsc(Long billId);

    @Query(value = """
            select
              p.id as id,
              p.pay_no as payNo,
              p.bill_id as billId,
              p.supplier_id as supplierId,
              p.pay_date as payDate,
              p.amount as amount,
              p.method as method,
              p.remark as remark,
              p.status as status,
              p.create_by as createBy,
              p.create_time as createTime,
              p.cancel_by as cancelBy,
              p.cancel_time as cancelTime
            from pur_ap_payment p
            where p.bill_id = :billId
            order by p.id asc
            """, nativeQuery = true)
    List<PurApPaymentRow> listRows(@Param("billId") Long billId);

    @Query(value = """
            select coalesce(sum(p.amount), 0.00)
            from pur_ap_payment p
            where p.bill_id = :billId
              and p.status = 2
            """, nativeQuery = true)
    BigDecimal sumCompletedAmount(@Param("billId") Long billId);

    interface PurApPaymentRow {
        Long getId();

        String getPayNo();

        Long getBillId();

        Long getSupplierId();

        LocalDate getPayDate();

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

