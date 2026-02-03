package com.ordererp.backend.wms.repository;

import com.ordererp.backend.wms.entity.WmsCheckBillDetail;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WmsCheckBillDetailRepository extends JpaRepository<WmsCheckBillDetail, Long> {
    List<WmsCheckBillDetail> findByBillId(Long billId);

    @Query(value = """
            select
              d.id as id,
              d.product_id as productId,
              p.product_code as productCode,
              p.product_name as productName,
              p.unit as unit,
              d.counted_qty as countedQty,
              d.book_qty as bookQty,
              d.diff_qty as diffQty
            from wms_check_bill_detail d
            left join base_product p on p.id = d.product_id
            where d.bill_id = :billId
            order by d.id asc
            """, nativeQuery = true)
    List<CheckBillItemRow> listRows(@Param("billId") Long billId);

    interface CheckBillItemRow {
        Long getId();

        Long getProductId();

        String getProductCode();

        String getProductName();

        String getUnit();

        BigDecimal getCountedQty();

        BigDecimal getBookQty();

        BigDecimal getDiffQty();
    }
}

