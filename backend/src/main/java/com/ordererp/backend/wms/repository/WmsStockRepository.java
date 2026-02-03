package com.ordererp.backend.wms.repository;

import com.ordererp.backend.wms.entity.WmsStock;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WmsStockRepository extends JpaRepository<WmsStock, Long> {
    Optional<WmsStock> findFirstByWarehouseIdAndProductId(Long warehouseId, Long productId);

    @Query(value = """
            select
              s.id as id,
              s.warehouse_id as warehouseId,
              w.warehouse_name as warehouseName,
              s.product_id as productId,
              p.product_code as productCode,
              p.product_name as productName,
              p.unit as unit,
              s.stock_qty as stockQty,
              s.locked_qty as lockedQty,
              s.update_time as updateTime
            from wms_stock s
            left join base_warehouse w on w.id = s.warehouse_id
            left join base_product p on p.id = s.product_id
            where (:warehouseId is null or s.warehouse_id = :warehouseId)
              and (:kw is null or :kw = ''
                   or lower(p.product_code) like lower(concat('%', :kw, '%'))
                   or lower(p.product_name) like lower(concat('%', :kw, '%'))
                   or lower(w.warehouse_code) like lower(concat('%', :kw, '%'))
                   or lower(w.warehouse_name) like lower(concat('%', :kw, '%')))
            order by s.id desc
            """,
            countQuery = """
            select count(*)
            from wms_stock s
            left join base_warehouse w on w.id = s.warehouse_id
            left join base_product p on p.id = s.product_id
            where (:warehouseId is null or s.warehouse_id = :warehouseId)
              and (:kw is null or :kw = ''
                   or lower(p.product_code) like lower(concat('%', :kw, '%'))
                   or lower(p.product_name) like lower(concat('%', :kw, '%'))
                   or lower(w.warehouse_code) like lower(concat('%', :kw, '%'))
                   or lower(w.warehouse_name) like lower(concat('%', :kw, '%')))
            """,
            nativeQuery = true)
    Page<StockRow> pageRows(@Param("kw") String keyword, @Param("warehouseId") Long warehouseId, Pageable pageable);

    interface StockRow {
        Long getId();

        Long getWarehouseId();

        String getWarehouseName();

        Long getProductId();

        String getProductCode();

        String getProductName();

        String getUnit();

        BigDecimal getStockQty();

        BigDecimal getLockedQty();

        LocalDateTime getUpdateTime();
    }
}

