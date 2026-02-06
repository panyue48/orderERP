package com.ordererp.backend.sales.repository;

import com.ordererp.backend.sales.entity.SalOrder;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

public interface SalOrderRepository extends JpaRepository<SalOrder, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from SalOrder o where o.id = :id")
    Optional<SalOrder> findByIdForUpdate(@Param("id") Long id);

    @Query(value = """
            select
              o.id as id,
              o.order_no as orderNo,
              o.customer_id as customerId,
              o.customer_code as customerCode,
              o.customer_name as customerName,
              o.warehouse_id as warehouseId,
              w.warehouse_name as warehouseName,
              o.order_date as orderDate,
              o.total_amount as totalAmount,
              o.status as status,
              o.remark as remark,
              o.wms_bill_id as wmsBillId,
              o.wms_bill_no as wmsBillNo,
              o.create_by as createBy,
              o.create_time as createTime,
              o.audit_by as auditBy,
              o.audit_time as auditTime,
              o.ship_by as shipBy,
              o.ship_time as shipTime
            from sal_order o
            left join base_warehouse w on w.id = o.warehouse_id
            where (:kw is null or :kw = ''
                   or lower(o.order_no) like lower(concat('%', :kw, '%'))
                   or lower(o.customer_name) like lower(concat('%', :kw, '%'))
                   or lower(o.customer_code) like lower(concat('%', :kw, '%')))
              and (:customerId is null or o.customer_id = :customerId)
              and (:startDate is null or o.order_date >= :startDate)
              and (:endDate is null or o.order_date <= :endDate)
            order by o.id desc
            """,
            countQuery = """
            select count(*)
            from sal_order o
            where (:kw is null or :kw = ''
                   or lower(o.order_no) like lower(concat('%', :kw, '%'))
                   or lower(o.customer_name) like lower(concat('%', :kw, '%'))
                   or lower(o.customer_code) like lower(concat('%', :kw, '%')))
              and (:customerId is null or o.customer_id = :customerId)
              and (:startDate is null or o.order_date >= :startDate)
              and (:endDate is null or o.order_date <= :endDate)
            """,
            nativeQuery = true)
    Page<SalOrderRow> pageRows(@Param("kw") String keyword,
            @Param("customerId") Long customerId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);

    @Query(value = """
            select
              o.id as id,
              o.order_no as orderNo,
              o.customer_id as customerId,
              o.customer_code as customerCode,
              o.customer_name as customerName,
              o.warehouse_id as warehouseId,
              w.warehouse_name as warehouseName,
              o.order_date as orderDate,
              o.total_amount as totalAmount,
              o.status as status,
              o.remark as remark,
              o.wms_bill_id as wmsBillId,
              o.wms_bill_no as wmsBillNo,
              o.create_by as createBy,
              o.create_time as createTime,
              o.audit_by as auditBy,
              o.audit_time as auditTime,
              o.ship_by as shipBy,
              o.ship_time as shipTime,
              o.cancel_by as cancelBy,
              o.cancel_time as cancelTime
            from sal_order o
            left join base_warehouse w on w.id = o.warehouse_id
            where o.id = :id
            """, nativeQuery = true)
    SalOrderDetailRow getDetailRow(@Param("id") Long id);

    @Query(value = """
            select
              o.id as id,
              o.order_no as orderNo,
              o.customer_id as customerId,
              o.customer_code as customerCode,
              o.customer_name as customerName,
              o.warehouse_id as warehouseId,
              w.warehouse_name as warehouseName,
              o.order_date as orderDate,
              o.status as status
            from sal_order o
            left join base_warehouse w on w.id = o.warehouse_id
            where o.status in (2, 3)
              and (:kw is null or :kw = ''
                   or lower(o.order_no) like lower(concat('%', :kw, '%'))
                   or lower(o.customer_name) like lower(concat('%', :kw, '%'))
                   or lower(o.customer_code) like lower(concat('%', :kw, '%')))
            order by o.id desc
            limit :limit
            """, nativeQuery = true)
    List<SalOrderOptionRow> optionRows(@Param("kw") String keyword, @Param("limit") int limit);

    interface SalOrderRow {
        Long getId();

        String getOrderNo();

        Long getCustomerId();

        String getCustomerCode();

        String getCustomerName();

        Long getWarehouseId();

        String getWarehouseName();

        LocalDate getOrderDate();

        BigDecimal getTotalAmount();

        Integer getStatus();

        String getRemark();

        Long getWmsBillId();

        String getWmsBillNo();

        String getCreateBy();

        LocalDateTime getCreateTime();

        String getAuditBy();

        LocalDateTime getAuditTime();

        String getShipBy();

        LocalDateTime getShipTime();
    }

    interface SalOrderDetailRow extends SalOrderRow {
        String getCancelBy();

        LocalDateTime getCancelTime();
    }

    interface SalOrderOptionRow {
        Long getId();

        String getOrderNo();

        Long getCustomerId();

        String getCustomerCode();

        String getCustomerName();

        Long getWarehouseId();

        String getWarehouseName();

        LocalDate getOrderDate();

        Integer getStatus();
    }
}
