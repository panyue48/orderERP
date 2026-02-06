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
              d.id as id,
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

    interface ReturnItemRow {
        Long getId();

        Long getProductId();

        String getProductCode();

        String getProductName();

        String getUnit();

        BigDecimal getPrice();

        BigDecimal getQty();

        BigDecimal getAmount();
    }
}

