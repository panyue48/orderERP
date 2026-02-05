package com.ordererp.backend.purchase.repository;

import com.ordererp.backend.purchase.entity.PurApInvoice;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurApInvoiceRepository extends JpaRepository<PurApInvoice, Long> {
    List<PurApInvoice> findByBillIdOrderByIdAsc(Long billId);

    @Query(value = """
            select
              i.id as id,
              i.invoice_no as invoiceNo,
              i.bill_id as billId,
              i.supplier_id as supplierId,
              i.invoice_date as invoiceDate,
              i.amount as amount,
              i.tax_amount as taxAmount,
              i.remark as remark,
              i.status as status,
              i.create_by as createBy,
              i.create_time as createTime,
              i.cancel_by as cancelBy,
              i.cancel_time as cancelTime
            from pur_ap_invoice i
            where i.bill_id = :billId
            order by i.id asc
            """, nativeQuery = true)
    List<PurApInvoiceRow> listRows(@Param("billId") Long billId);

    @Query(value = """
            select coalesce(sum(i.amount), 0.00)
            from pur_ap_invoice i
            where i.bill_id = :billId
              and i.status = 2
            """, nativeQuery = true)
    BigDecimal sumValidAmount(@Param("billId") Long billId);

    interface PurApInvoiceRow {
        Long getId();

        String getInvoiceNo();

        Long getBillId();

        Long getSupplierId();

        LocalDate getInvoiceDate();

        BigDecimal getAmount();

        BigDecimal getTaxAmount();

        String getRemark();

        Integer getStatus();

        String getCreateBy();

        LocalDateTime getCreateTime();

        String getCancelBy();

        LocalDateTime getCancelTime();
    }
}

