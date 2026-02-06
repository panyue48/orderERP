package com.ordererp.backend.purchase.repository;

import com.ordererp.backend.purchase.entity.PurInbound;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurInboundRepository extends JpaRepository<PurInbound, Long> {
    Optional<PurInbound> findFirstByRequestNo(String requestNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from PurInbound i where i.id = :id")
    Optional<PurInbound> findByIdForUpdate(@Param("id") Long id);

    @Query(value = """
            select
              i.id as id,
              i.inbound_no as inboundNo,
              i.request_no as requestNo,
              i.order_id as orderId,
              i.order_no as orderNo,
              i.supplier_id as supplierId,
              p.partner_code as supplierCode,
              p.partner_name as supplierName,
              i.warehouse_id as warehouseId,
              w.warehouse_name as warehouseName,
              i.status as status,
              i.qc_status as qcStatus,
              i.qc_by as qcBy,
              i.qc_time as qcTime,
              i.qc_remark as qcRemark,
              i.wms_bill_id as wmsBillId,
              i.wms_bill_no as wmsBillNo,
              i.reverse_status as reverseStatus,
              i.reverse_by as reverseBy,
              i.reverse_time as reverseTime,
              i.reverse_wms_bill_id as reverseWmsBillId,
              i.reverse_wms_bill_no as reverseWmsBillNo,
              i.remark as remark,
              i.create_by as createBy,
              i.create_time as createTime,
              i.execute_by as executeBy,
              i.execute_time as executeTime
            from pur_inbound i
            left join base_partner p on p.id = i.supplier_id
            left join base_warehouse w on w.id = i.warehouse_id
            where (:orderId is null or i.order_id = :orderId)
              and (:kw is null or :kw = ''
              or lower(i.inbound_no) like lower(concat('%', :kw, '%'))
              or lower(i.order_no) like lower(concat('%', :kw, '%'))
              or lower(p.partner_code) like lower(concat('%', :kw, '%'))
              or lower(p.partner_name) like lower(concat('%', :kw, '%'))
              or lower(i.wms_bill_no) like lower(concat('%', :kw, '%')))
            order by i.id desc
            """,
            countQuery = """
            select count(*)
            from pur_inbound i
            left join base_partner p on p.id = i.supplier_id
            where (:orderId is null or i.order_id = :orderId)
              and (:kw is null or :kw = ''
              or lower(i.inbound_no) like lower(concat('%', :kw, '%'))
              or lower(i.order_no) like lower(concat('%', :kw, '%'))
              or lower(p.partner_code) like lower(concat('%', :kw, '%'))
              or lower(p.partner_name) like lower(concat('%', :kw, '%'))
              or lower(i.wms_bill_no) like lower(concat('%', :kw, '%')))
            """,
            nativeQuery = true)
    Page<PurInboundRow> pageRows(@Param("kw") String keyword, @Param("orderId") Long orderId, Pageable pageable);

    @Query(value = """
            select
              (select count(*)
               from pur_inbound i2
               where i2.order_id = :orderId
                 and i2.status = 1
                 and i2.qc_status = 1) as pendingCount,
              (select coalesce(sum(d.plan_qty), 0.000)
               from pur_inbound i3
               join pur_inbound_detail d on d.inbound_id = i3.id
               where i3.order_id = :orderId
                 and i3.status = 1
                 and i3.qc_status = 1) as pendingQty
            """, nativeQuery = true)
    PendingQcSummaryRow pendingQcSummary(@Param("orderId") Long orderId);

    @Query(value = """
            select
              d.product_id as productId,
              d.product_code as productCode,
              d.product_name as productName,
              d.unit as unit,
              coalesce(sum(d.plan_qty), 0.000) as qty
            from pur_inbound i
            join pur_inbound_detail d on d.inbound_id = i.id
            where i.order_id = :orderId
              and i.status = 1
              and i.qc_status = 1
            group by d.product_id, d.product_code, d.product_name, d.unit
            order by d.product_id asc
            """, nativeQuery = true)
    java.util.List<PendingQcItemRow> pendingQcSummaryItems(@Param("orderId") Long orderId);

    @Query(value = """
            select
              i.id as id,
              i.inbound_no as inboundNo,
              i.request_no as requestNo,
              i.order_id as orderId,
              i.order_no as orderNo,
              i.supplier_id as supplierId,
              p.partner_code as supplierCode,
              p.partner_name as supplierName,
              i.warehouse_id as warehouseId,
              w.warehouse_name as warehouseName,
              i.status as status,
              i.qc_status as qcStatus,
              i.qc_by as qcBy,
              i.qc_time as qcTime,
              i.qc_remark as qcRemark,
              i.wms_bill_id as wmsBillId,
              i.wms_bill_no as wmsBillNo,
              i.reverse_status as reverseStatus,
              i.reverse_by as reverseBy,
              i.reverse_time as reverseTime,
              i.reverse_wms_bill_id as reverseWmsBillId,
              i.reverse_wms_bill_no as reverseWmsBillNo,
              i.remark as remark,
              i.create_by as createBy,
              i.create_time as createTime,
              i.execute_by as executeBy,
              i.execute_time as executeTime
            from pur_inbound i
            left join base_partner p on p.id = i.supplier_id
            left join base_warehouse w on w.id = i.warehouse_id
            where i.id = :id
            """,
            nativeQuery = true)
    PurInboundRow getRow(@Param("id") Long id);

    interface PurInboundRow {
        Long getId();

        String getInboundNo();

        String getRequestNo();

        Long getOrderId();

        String getOrderNo();

        Long getSupplierId();

        String getSupplierCode();

        String getSupplierName();

        Long getWarehouseId();

        String getWarehouseName();

        Integer getStatus();

        Integer getQcStatus();

        String getQcBy();

        LocalDateTime getQcTime();

        String getQcRemark();

        Long getWmsBillId();

        String getWmsBillNo();

        Integer getReverseStatus();

        String getReverseBy();

        LocalDateTime getReverseTime();

        Long getReverseWmsBillId();

        String getReverseWmsBillNo();

        String getRemark();

        String getCreateBy();

        LocalDateTime getCreateTime();

        String getExecuteBy();

        LocalDateTime getExecuteTime();
    }

    interface PendingQcSummaryRow {
        Long getPendingCount();

        java.math.BigDecimal getPendingQty();
    }

    interface PendingQcItemRow {
        Long getProductId();

        String getProductCode();

        String getProductName();

        String getUnit();

        java.math.BigDecimal getQty();
    }
}
