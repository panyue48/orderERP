package com.ordererp.backend.sales.repository;

import com.ordererp.backend.sales.entity.SalShip;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

public interface SalShipRepository extends JpaRepository<SalShip, Long> {
    List<SalShip> findByOrderIdOrderByIdAsc(Long orderId);

    Optional<SalShip> findFirstByRequestNo(String requestNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SalShip s where s.id = :id")
    Optional<SalShip> findByIdForUpdate(@Param("id") Long id);

    @Query(value = """
            select
              s.id as id,
              s.ship_no as shipNo,
              s.order_id as orderId,
              s.order_no as orderNo,
              s.customer_id as customerId,
              s.customer_code as customerCode,
              s.customer_name as customerName,
              s.warehouse_id as warehouseId,
              w.warehouse_name as warehouseName,
              s.ship_time as shipTime,
              s.total_qty as totalQty,
              s.wms_bill_id as wmsBillId,
              s.wms_bill_no as wmsBillNo,
              s.reverse_status as reverseStatus,
              s.reverse_by as reverseBy,
              s.reverse_time as reverseTime,
              s.reverse_wms_bill_id as reverseWmsBillId,
              s.reverse_wms_bill_no as reverseWmsBillNo,
              s.create_by as createBy,
              s.create_time as createTime
            from sal_ship s
            left join base_warehouse w on w.id = s.warehouse_id
            where (:kw is null or :kw = ''
                   or lower(s.ship_no) like lower(concat('%', :kw, '%'))
                   or lower(s.order_no) like lower(concat('%', :kw, '%'))
                   or lower(s.customer_name) like lower(concat('%', :kw, '%'))
                   or lower(s.customer_code) like lower(concat('%', :kw, '%'))
                   or lower(s.wms_bill_no) like lower(concat('%', :kw, '%')))
              and (:customerId is null or s.customer_id = :customerId)
              and (:warehouseId is null or s.warehouse_id = :warehouseId)
              and (:startDate is null or date(s.ship_time) >= :startDate)
              and (:endDate is null or date(s.ship_time) <= :endDate)
            order by s.id desc
            """,
            countQuery = """
            select count(*)
            from sal_ship s
            where (:kw is null or :kw = ''
                   or lower(s.ship_no) like lower(concat('%', :kw, '%'))
                   or lower(s.order_no) like lower(concat('%', :kw, '%'))
                   or lower(s.customer_name) like lower(concat('%', :kw, '%'))
                   or lower(s.customer_code) like lower(concat('%', :kw, '%'))
                   or lower(s.wms_bill_no) like lower(concat('%', :kw, '%')))
              and (:customerId is null or s.customer_id = :customerId)
              and (:warehouseId is null or s.warehouse_id = :warehouseId)
              and (:startDate is null or date(s.ship_time) >= :startDate)
              and (:endDate is null or date(s.ship_time) <= :endDate)
            """,
            nativeQuery = true)
    Page<ShipRow> pageRows(@Param("kw") String keyword,
            @Param("customerId") Long customerId,
            @Param("warehouseId") Long warehouseId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);

    @Query(value = """
            select
              s.id as id,
              s.ship_no as shipNo,
              s.order_id as orderId,
              s.order_no as orderNo,
              s.customer_id as customerId,
              s.customer_code as customerCode,
              s.customer_name as customerName,
              s.warehouse_id as warehouseId,
              w.warehouse_name as warehouseName,
              s.ship_time as shipTime,
              s.total_qty as totalQty,
              s.wms_bill_id as wmsBillId,
              s.wms_bill_no as wmsBillNo,
              s.reverse_status as reverseStatus,
              s.reverse_by as reverseBy,
              s.reverse_time as reverseTime,
              s.reverse_wms_bill_id as reverseWmsBillId,
              s.reverse_wms_bill_no as reverseWmsBillNo,
              s.create_by as createBy,
              s.create_time as createTime
            from sal_ship s
            left join base_warehouse w on w.id = s.warehouse_id
            where s.order_id = :orderId
            order by s.id asc
            """, nativeQuery = true)
    List<ShipRow> listRows(@Param("orderId") Long orderId);

    @Query(value = """
            select
              s.id as id,
              s.ship_no as shipNo,
              s.order_id as orderId,
              s.order_no as orderNo,
              s.customer_id as customerId,
              s.customer_code as customerCode,
              s.customer_name as customerName,
              s.warehouse_id as warehouseId,
              w.warehouse_name as warehouseName,
              s.ship_time as shipTime,
              s.total_qty as totalQty,
              s.wms_bill_id as wmsBillId,
              s.wms_bill_no as wmsBillNo,
              s.reverse_status as reverseStatus,
              s.reverse_by as reverseBy,
              s.reverse_time as reverseTime,
              s.reverse_wms_bill_id as reverseWmsBillId,
              s.reverse_wms_bill_no as reverseWmsBillNo,
              s.create_by as createBy,
              s.create_time as createTime
            from sal_ship s
            left join base_warehouse w on w.id = s.warehouse_id
            where s.id = :id
            """, nativeQuery = true)
    ShipRow getRow(@Param("id") Long id);

    interface ShipRow {
        Long getId();

        String getShipNo();

        Long getOrderId();

        String getOrderNo();

        Long getCustomerId();

        String getCustomerCode();

        String getCustomerName();

        Long getWarehouseId();

        String getWarehouseName();

        LocalDateTime getShipTime();

        BigDecimal getTotalQty();

        Long getWmsBillId();

        String getWmsBillNo();

        Integer getReverseStatus();

        String getReverseBy();

        LocalDateTime getReverseTime();

        Long getReverseWmsBillId();

        String getReverseWmsBillNo();

        String getCreateBy();

        LocalDateTime getCreateTime();
    }
}
