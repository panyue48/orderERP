package com.ordererp.backend.sales.repository;

import com.ordererp.backend.sales.entity.SalShipDetail;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalShipDetailRepository extends JpaRepository<SalShipDetail, Long> {
    List<SalShipDetail> findByShipIdOrderByIdAsc(Long shipId);

    @Query(value = """
            select
              d.id as id,
              d.ship_id as shipId,
              d.order_id as orderId,
              d.order_detail_id as orderDetailId,
              d.product_id as productId,
              d.product_code as productCode,
              d.product_name as productName,
              d.unit as unit,
              d.qty as qty
            from sal_ship_detail d
            where d.ship_id = :shipId
            order by d.id asc
            """, nativeQuery = true)
    List<ShipItemRow> listRows(@Param("shipId") Long shipId);

    interface ShipItemRow {
        Long getId();

        Long getShipId();

        Long getOrderId();

        Long getOrderDetailId();

        Long getProductId();

        String getProductCode();

        String getProductName();

        String getUnit();

        BigDecimal getQty();
    }
}

