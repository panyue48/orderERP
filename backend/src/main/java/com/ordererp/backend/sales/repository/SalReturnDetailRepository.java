package com.ordererp.backend.sales.repository;

import com.ordererp.backend.sales.entity.SalReturnDetail;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalReturnDetailRepository extends JpaRepository<SalReturnDetail, Long> {
    List<SalReturnDetail> findByReturnIdOrderByIdAsc(Long returnId);

    @Query(value = """
            select
              d.ship_detail_id as shipDetailId,
              coalesce(sum(d.qty), 0.000) as returnedQty
            from sal_return_detail d
            join sal_return r on r.id = d.return_id
            where d.ship_detail_id in (:shipDetailIds)
              and r.status not in (5, 9)
            group by d.ship_detail_id
            """, nativeQuery = true)
    List<ShipDetailReturnedRow> sumReturnedQtyByShipDetailIds(@Param("shipDetailIds") List<Long> shipDetailIds);

    @Query(value = """
            select
              d.ship_detail_id as shipDetailId,
              coalesce(sum(d.qty), 0.000) as returnedQty
            from sal_return_detail d
            join sal_return r on r.id = d.return_id
            where r.ship_id = :shipId
              and r.status not in (5, 9)
            group by d.ship_detail_id
            """, nativeQuery = true)
    List<ShipDetailReturnedRow> sumReturnedQtyByShipId(@Param("shipId") Long shipId);

    @Query(value = """
            select
              d.id as id,
              d.ship_detail_id as shipDetailId,
              d.order_detail_id as orderDetailId,
              d.product_id as productId,
              d.product_code as productCode,
              d.product_name as productName,
              d.unit as unit,
              d.price as price,
              d.qty as qty,
              d.amount as amount
            from sal_return_detail d
            where d.return_id = :returnId
            order by d.id asc
            """, nativeQuery = true)
    List<ReturnItemRow> listRows(@Param("returnId") Long returnId);

    interface ShipDetailReturnedRow {
        Long getShipDetailId();

        BigDecimal getReturnedQty();
    }

    interface ReturnItemRow {
        Long getId();

        Long getShipDetailId();

        Long getOrderDetailId();

        Long getProductId();

        String getProductCode();

        String getProductName();

        String getUnit();

        BigDecimal getPrice();

        BigDecimal getQty();

        BigDecimal getAmount();
    }
}
