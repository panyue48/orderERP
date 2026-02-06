package com.ordererp.backend.purchase.repository;

import com.ordererp.backend.purchase.entity.PurOrderDetail;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PurOrderDetailRepository extends JpaRepository<PurOrderDetail, Long> {
    List<PurOrderDetail> findByOrderIdOrderByIdAsc(Long orderId);

    @Query(value = """
            select
              o.order_no as orderNo,
              p.partner_code as supplierCode,
              p.partner_name as supplierName,
              o.order_date as orderDate,
              o.status as status,
              d.product_code as productCode,
              d.product_name as productName,
              d.unit as unit,
              d.price as price,
              d.qty as qty,
              d.amount as amount,
              d.in_qty as inQty,
              o.remark as remark
            from pur_order o
            join pur_order_detail d on d.order_id = o.id
            left join base_partner p on p.id = o.supplier_id
            where (:kw is null or :kw = ''
              or lower(o.order_no) like lower(concat('%', :kw, '%'))
              or lower(p.partner_code) like lower(concat('%', :kw, '%'))
              or lower(p.partner_name) like lower(concat('%', :kw, '%'))
              or lower(d.product_code) like lower(concat('%', :kw, '%'))
              or lower(d.product_name) like lower(concat('%', :kw, '%')))
            order by o.id desc, d.id asc
            """,
            countQuery = """
            select count(*)
            from pur_order o
            join pur_order_detail d on d.order_id = o.id
            left join base_partner p on p.id = o.supplier_id
            where (:kw is null or :kw = ''
              or lower(o.order_no) like lower(concat('%', :kw, '%'))
              or lower(p.partner_code) like lower(concat('%', :kw, '%'))
              or lower(p.partner_name) like lower(concat('%', :kw, '%'))
              or lower(d.product_code) like lower(concat('%', :kw, '%'))
              or lower(d.product_name) like lower(concat('%', :kw, '%')))
            """,
            nativeQuery = true)
    Page<PurOrderExportRow> exportRows(@Param("kw") String keyword, Pageable pageable);

    interface PurOrderExportRow {
        String getOrderNo();

        String getSupplierCode();

        String getSupplierName();

        LocalDate getOrderDate();

        Integer getStatus();

        String getProductCode();

        String getProductName();

        String getUnit();

        BigDecimal getPrice();

        BigDecimal getQty();

        BigDecimal getAmount();

        BigDecimal getInQty();

        String getRemark();
    }
}
