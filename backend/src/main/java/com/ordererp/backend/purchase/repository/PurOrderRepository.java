package com.ordererp.backend.purchase.repository;

import com.ordererp.backend.purchase.entity.PurOrder;
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

public interface PurOrderRepository extends JpaRepository<PurOrder, Long> {
    Optional<PurOrder> findFirstByOrderNo(String orderNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PurOrder o where o.id = :id")
    Optional<PurOrder> findByIdForUpdate(@Param("id") Long id);

    @Query(value = """
            select
              o.id as id,
              o.order_no as orderNo,
              o.supplier_id as supplierId,
              p.partner_code as supplierCode,
              p.partner_name as supplierName,
              o.order_date as orderDate,
              o.total_amount as totalAmount,
              o.pay_amount as payAmount,
              o.status as status,
              o.remark as remark,
              o.create_by as createBy,
              o.create_time as createTime,
              o.audit_by as auditBy,
              o.audit_time as auditTime
            from pur_order o
            left join base_partner p on p.id = o.supplier_id
            where (:kw is null or :kw = ''
              or lower(o.order_no) like lower(concat('%', :kw, '%'))
              or lower(p.partner_code) like lower(concat('%', :kw, '%'))
              or lower(p.partner_name) like lower(concat('%', :kw, '%')))
            order by o.id desc
            """,
            countQuery = """
            select count(*)
            from pur_order o
            left join base_partner p on p.id = o.supplier_id
            where (:kw is null or :kw = ''
              or lower(o.order_no) like lower(concat('%', :kw, '%'))
              or lower(p.partner_code) like lower(concat('%', :kw, '%'))
              or lower(p.partner_name) like lower(concat('%', :kw, '%')))
            """,
            nativeQuery = true)
    Page<PurOrderRow> pageRows(@Param("kw") String keyword, Pageable pageable);

    @Query(value = """
            select
              o.id as id,
              o.order_no as orderNo,
              o.supplier_id as supplierId,
              p.partner_code as supplierCode,
              p.partner_name as supplierName,
              o.order_date as orderDate,
              o.total_amount as totalAmount,
              o.pay_amount as payAmount,
              o.status as status,
              o.remark as remark,
              o.create_by as createBy,
              o.create_time as createTime,
              o.audit_by as auditBy,
              o.audit_time as auditTime
            from pur_order o
            left join base_partner p on p.id = o.supplier_id
            where o.id = :id
            """,
            nativeQuery = true)
    PurOrderRow getRow(@Param("id") Long id);

    @Query(value = """
            select
              o.id as id,
              o.order_no as orderNo,
              o.supplier_id as supplierId,
              p.partner_code as supplierCode,
              p.partner_name as supplierName,
              o.order_date as orderDate,
              o.status as status
            from pur_order o
            left join base_partner p on p.id = o.supplier_id
            where o.status in (2, 3)
              and (:kw is null or :kw = ''
                   or lower(o.order_no) like lower(concat('%', :kw, '%'))
                   or lower(p.partner_code) like lower(concat('%', :kw, '%'))
                   or lower(p.partner_name) like lower(concat('%', :kw, '%')))
            order by o.id desc
            limit :limit
            """, nativeQuery = true)
    java.util.List<PurOrderOptionRow> optionRows(@Param("kw") String keyword, @Param("limit") int limit);

    interface PurOrderRow {
        Long getId();

        String getOrderNo();

        Long getSupplierId();

        String getSupplierCode();

        String getSupplierName();

        LocalDate getOrderDate();

        BigDecimal getTotalAmount();

        BigDecimal getPayAmount();

        Integer getStatus();

        String getRemark();

        String getCreateBy();

        LocalDateTime getCreateTime();

        String getAuditBy();

        LocalDateTime getAuditTime();
    }

    interface PurOrderOptionRow {
        Long getId();

        String getOrderNo();

        Long getSupplierId();

        String getSupplierCode();

        String getSupplierName();

        LocalDate getOrderDate();

        Integer getStatus();
    }
}
