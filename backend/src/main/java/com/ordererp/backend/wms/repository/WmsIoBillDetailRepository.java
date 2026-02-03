package com.ordererp.backend.wms.repository;

import com.ordererp.backend.wms.entity.WmsIoBillDetail;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WmsIoBillDetailRepository extends JpaRepository<WmsIoBillDetail, Long> {
    List<WmsIoBillDetail> findByBillId(Long billId);

    @Query(value = """
            select
              d.id as id,
              d.product_id as productId,
              p.product_code as productCode,
              p.product_name as productName,
              p.unit as unit,
              d.qty as qty,
              d.real_qty as realQty
            from wms_io_bill_detail d
            left join base_product p on p.id = d.product_id
            where d.bill_id = :billId
            order by d.id asc
            """, nativeQuery = true)
    List<BillItemRow> listBillItemRows(@Param("billId") Long billId);

    interface BillItemRow {
        Long getId();

        Long getProductId();

        String getProductCode();

        String getProductName();

        String getUnit();

        BigDecimal getQty();

        BigDecimal getRealQty();
    }
}

