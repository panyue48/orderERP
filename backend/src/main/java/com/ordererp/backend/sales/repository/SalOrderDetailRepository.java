package com.ordererp.backend.sales.repository;

import com.ordererp.backend.sales.entity.SalOrderDetail;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalOrderDetailRepository extends JpaRepository<SalOrderDetail, Long> {
    List<SalOrderDetail> findByOrderIdOrderByIdAsc(Long orderId);

    long deleteByOrderId(Long orderId);

    @Query(value = """
            select
              d.id as id,
              d.order_id as orderId,
              d.product_id as productId,
              d.product_code as productCode,
              d.product_name as productName,
              d.unit as unit,
              d.price as price,
              d.qty as qty,
              d.shipped_qty as shippedQty,
              d.amount as amount
            from sal_order_detail d
            where d.order_id = :orderId
            order by d.id asc
            """, nativeQuery = true)
    List<SalOrderItemRow> listItemRows(@Param("orderId") Long orderId);

    interface SalOrderItemRow {
        Long getId();

        Long getOrderId();

        Long getProductId();

        String getProductCode();

        String getProductName();

        String getUnit();

        BigDecimal getPrice();

        BigDecimal getQty();

        BigDecimal getShippedQty();

        BigDecimal getAmount();
    }
}
