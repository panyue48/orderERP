package com.ordererp.backend.purchase.repository;

import com.ordererp.backend.purchase.entity.PurReturn;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurReturnRepository extends JpaRepository<PurReturn, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PurReturn r where r.id = :id")
    Optional<PurReturn> findByIdForUpdate(@Param("id") Long id);

    @Query(value = """
            select
              r.id as id,
              r.return_no as returnNo,
              r.supplier_id as supplierId,
              p.partner_code as supplierCode,
              p.partner_name as supplierName,
              r.warehouse_id as warehouseId,
              w.warehouse_name as warehouseName,
              r.return_date as returnDate,
              r.total_amount as totalAmount,
              r.status as status,
              r.wms_bill_id as wmsBillId,
              r.wms_bill_no as wmsBillNo,
              r.remark as remark,
              r.create_by as createBy,
              r.create_time as createTime,
              r.audit_by as auditBy,
              r.audit_time as auditTime,
              r.execute_by as executeBy,
              r.execute_time as executeTime,
              coalesce((select sum(d.qty) from pur_return_detail d where d.return_id = r.id), 0) as totalQty
            from pur_return r
            left join base_partner p on p.id = r.supplier_id
            left join base_warehouse w on w.id = r.warehouse_id
            where (:kw is null or :kw = ''
              or lower(r.return_no) like lower(concat('%', :kw, '%'))
              or lower(p.partner_code) like lower(concat('%', :kw, '%'))
              or lower(p.partner_name) like lower(concat('%', :kw, '%'))
              or lower(r.wms_bill_no) like lower(concat('%', :kw, '%')))
            order by r.id desc
            """,
            countQuery = """
            select count(*)
            from pur_return r
            left join base_partner p on p.id = r.supplier_id
            where (:kw is null or :kw = ''
              or lower(r.return_no) like lower(concat('%', :kw, '%'))
              or lower(p.partner_code) like lower(concat('%', :kw, '%'))
              or lower(p.partner_name) like lower(concat('%', :kw, '%'))
              or lower(r.wms_bill_no) like lower(concat('%', :kw, '%')))
            """,
            nativeQuery = true)
    Page<PurReturnRow> pageRows(@Param("kw") String keyword, Pageable pageable);

    @Query(value = """
            select
              r.id as id,
              r.return_no as returnNo,
              r.supplier_id as supplierId,
              p.partner_code as supplierCode,
              p.partner_name as supplierName,
              r.warehouse_id as warehouseId,
              w.warehouse_name as warehouseName,
              r.return_date as returnDate,
              r.total_amount as totalAmount,
              r.status as status,
              r.wms_bill_id as wmsBillId,
              r.wms_bill_no as wmsBillNo,
              r.remark as remark,
              r.create_by as createBy,
              r.create_time as createTime,
              r.audit_by as auditBy,
              r.audit_time as auditTime,
              r.execute_by as executeBy,
              r.execute_time as executeTime,
              coalesce((select sum(d.qty) from pur_return_detail d where d.return_id = r.id), 0) as totalQty
            from pur_return r
            left join base_partner p on p.id = r.supplier_id
            left join base_warehouse w on w.id = r.warehouse_id
            where r.id = :id
            """,
            nativeQuery = true)
    PurReturnRow getRow(@Param("id") Long id);

    interface PurReturnRow {
        Long getId();

        String getReturnNo();

        Long getSupplierId();

        String getSupplierCode();

        String getSupplierName();

        Long getWarehouseId();

        String getWarehouseName();

        LocalDate getReturnDate();

        BigDecimal getTotalAmount();

        Integer getStatus();

        Long getWmsBillId();

        String getWmsBillNo();

        String getRemark();

        String getCreateBy();

        LocalDateTime getCreateTime();

        String getAuditBy();

        LocalDateTime getAuditTime();

        String getExecuteBy();

        LocalDateTime getExecuteTime();

        BigDecimal getTotalQty();
    }
}

