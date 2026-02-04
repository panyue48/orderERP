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
              i.wms_bill_id as wmsBillId,
              i.wms_bill_no as wmsBillNo,
              i.remark as remark,
              i.create_by as createBy,
              i.create_time as createTime,
              i.execute_by as executeBy,
              i.execute_time as executeTime
            from pur_inbound i
            left join base_partner p on p.id = i.supplier_id
            left join base_warehouse w on w.id = i.warehouse_id
            where (:kw is null or :kw = ''
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
            where (:kw is null or :kw = ''
              or lower(i.inbound_no) like lower(concat('%', :kw, '%'))
              or lower(i.order_no) like lower(concat('%', :kw, '%'))
              or lower(p.partner_code) like lower(concat('%', :kw, '%'))
              or lower(p.partner_name) like lower(concat('%', :kw, '%'))
              or lower(i.wms_bill_no) like lower(concat('%', :kw, '%')))
            """,
            nativeQuery = true)
    Page<PurInboundRow> pageRows(@Param("kw") String keyword, Pageable pageable);

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
              i.wms_bill_id as wmsBillId,
              i.wms_bill_no as wmsBillNo,
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

        Long getWmsBillId();

        String getWmsBillNo();

        String getRemark();

        String getCreateBy();

        LocalDateTime getCreateTime();

        String getExecuteBy();

        LocalDateTime getExecuteTime();
    }
}

