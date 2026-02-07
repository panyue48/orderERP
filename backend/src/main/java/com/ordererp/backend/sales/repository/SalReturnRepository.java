package com.ordererp.backend.sales.repository;

import com.ordererp.backend.sales.entity.SalReturn;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalReturnRepository extends JpaRepository<SalReturn, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from SalReturn r where r.id = :id")
    Optional<SalReturn> findByIdForUpdate(@Param("id") Long id);

    @Query(value = """
            select
              r.id as id,
              r.return_no as returnNo,
              r.customer_id as customerId,
              r.customer_code as customerCode,
              r.customer_name as customerName,
              r.warehouse_id as warehouseId,
              w.warehouse_name as warehouseName,
              r.ship_id as shipId,
              r.ship_no as shipNo,
              r.order_id as orderId,
              r.order_no as orderNo,
              r.return_date as returnDate,
              coalesce((select sum(d.qty) from sal_return_detail d where d.return_id = r.id), 0) as totalQty,
              r.total_amount as totalAmount,
              r.status as status,
              r.remark as remark,
              r.wms_bill_id as wmsBillId,
              r.wms_bill_no as wmsBillNo,
              r.create_by as createBy,
              r.create_time as createTime,
              r.audit_by as auditBy,
              r.audit_time as auditTime,
              r.receive_by as receiveBy,
              r.receive_time as receiveTime,
              r.qc_by as qcBy,
              r.qc_time as qcTime,
              r.qc_disposition as qcDisposition,
              r.qc_remark as qcRemark,
              r.execute_by as executeBy,
              r.execute_time as executeTime
            from sal_return r
            left join base_warehouse w on w.id = r.warehouse_id
            where (:kw is null or :kw = ''
                   or lower(r.return_no) like lower(concat('%', :kw, '%'))
                   or lower(r.customer_name) like lower(concat('%', :kw, '%'))
                   or lower(r.customer_code) like lower(concat('%', :kw, '%'))
                   or lower(r.wms_bill_no) like lower(concat('%', :kw, '%')))
            order by r.id desc
            """,
            countQuery = """
            select count(*)
            from sal_return r
            where (:kw is null or :kw = ''
                   or lower(r.return_no) like lower(concat('%', :kw, '%'))
                   or lower(r.customer_name) like lower(concat('%', :kw, '%'))
                   or lower(r.customer_code) like lower(concat('%', :kw, '%'))
                   or lower(r.wms_bill_no) like lower(concat('%', :kw, '%')))
            """,
            nativeQuery = true)
    Page<SalReturnRow> pageRows(@Param("kw") String keyword, Pageable pageable);

    @Query(value = """
            select
              r.id as id,
              r.return_no as returnNo,
              r.customer_id as customerId,
              r.customer_code as customerCode,
              r.customer_name as customerName,
              r.warehouse_id as warehouseId,
              w.warehouse_name as warehouseName,
              r.ship_id as shipId,
              r.ship_no as shipNo,
              r.order_id as orderId,
              r.order_no as orderNo,
              r.return_date as returnDate,
              coalesce((select sum(d.qty) from sal_return_detail d where d.return_id = r.id), 0) as totalQty,
              r.total_amount as totalAmount,
              r.status as status,
              r.remark as remark,
              r.wms_bill_id as wmsBillId,
              r.wms_bill_no as wmsBillNo,
              r.create_by as createBy,
              r.create_time as createTime,
              r.audit_by as auditBy,
              r.audit_time as auditTime,
              r.receive_by as receiveBy,
              r.receive_time as receiveTime,
              r.qc_by as qcBy,
              r.qc_time as qcTime,
              r.qc_disposition as qcDisposition,
              r.qc_remark as qcRemark,
              r.execute_by as executeBy,
              r.execute_time as executeTime
            from sal_return r
            left join base_warehouse w on w.id = r.warehouse_id
            where r.id = :id
            """, nativeQuery = true)
    SalReturnRow getRow(@Param("id") Long id);

    interface SalReturnRow {
        Long getId();

        String getReturnNo();

        Long getCustomerId();

        String getCustomerCode();

        String getCustomerName();

        Long getWarehouseId();

        String getWarehouseName();

        Long getShipId();

        String getShipNo();

        Long getOrderId();

        String getOrderNo();

        LocalDate getReturnDate();

        BigDecimal getTotalQty();

        BigDecimal getTotalAmount();

        Integer getStatus();

        String getRemark();

        Long getWmsBillId();

        String getWmsBillNo();

        String getCreateBy();

        LocalDateTime getCreateTime();

        String getAuditBy();

        LocalDateTime getAuditTime();

        String getReceiveBy();

        LocalDateTime getReceiveTime();

        String getQcBy();

        LocalDateTime getQcTime();

        String getQcDisposition();

        String getQcRemark();

        String getExecuteBy();

        LocalDateTime getExecuteTime();
    }
}
