package com.ordererp.backend.wms.repository;

import com.ordererp.backend.wms.entity.WmsCheckBill;
import java.time.LocalDateTime;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WmsCheckBillRepository extends JpaRepository<WmsCheckBill, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from WmsCheckBill b where b.id = :id")
    Optional<WmsCheckBill> findByIdForUpdate(@Param("id") Long id);

    @Query(value = """
            select
              b.id as id,
              b.bill_no as billNo,
              b.warehouse_id as warehouseId,
              w.warehouse_name as warehouseName,
              b.status as status,
              b.remark as remark,
              b.create_by as createBy,
              b.create_time as createTime,
              b.execute_time as executeTime
            from wms_check_bill b
            left join base_warehouse w on w.id = b.warehouse_id
            where (:kw is null or :kw = ''
                   or lower(b.bill_no) like lower(concat('%', :kw, '%'))
                   or lower(w.warehouse_code) like lower(concat('%', :kw, '%'))
                   or lower(w.warehouse_name) like lower(concat('%', :kw, '%')))
            order by b.id desc
            """,
            countQuery = """
            select count(*)
            from wms_check_bill b
            left join base_warehouse w on w.id = b.warehouse_id
            where (:kw is null or :kw = ''
                   or lower(b.bill_no) like lower(concat('%', :kw, '%'))
                   or lower(w.warehouse_code) like lower(concat('%', :kw, '%'))
                   or lower(w.warehouse_name) like lower(concat('%', :kw, '%')))
            """,
            nativeQuery = true)
    Page<CheckBillRow> pageRows(@Param("kw") String keyword, Pageable pageable);

    @Query(value = """
            select
              b.id as id,
              b.bill_no as billNo,
              b.warehouse_id as warehouseId,
              w.warehouse_name as warehouseName,
              b.status as status,
              b.remark as remark,
              b.create_by as createBy,
              b.create_time as createTime,
              b.execute_time as executeTime
            from wms_check_bill b
            left join base_warehouse w on w.id = b.warehouse_id
            where b.id = :id
            """,
            nativeQuery = true)
    CheckBillRow getRow(@Param("id") Long id);

    interface CheckBillRow {
        Long getId();

        String getBillNo();

        Long getWarehouseId();

        String getWarehouseName();

        Integer getStatus();

        String getRemark();

        String getCreateBy();

        LocalDateTime getCreateTime();

        LocalDateTime getExecuteTime();
    }
}

