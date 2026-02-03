package com.ordererp.backend.wms.repository;

import com.ordererp.backend.wms.entity.WmsStockLog;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WmsStockLogRepository extends JpaRepository<WmsStockLog, Long> {
    long countByBizNoAndBizType(String bizNo, String bizType);

    List<WmsStockLog> findByBizNoAndBizType(String bizNo, String bizType);

    @Query(value = """
            select
              l.id as id,
              l.warehouse_id as warehouseId,
              w.warehouse_name as warehouseName,
              l.product_id as productId,
              p.product_code as productCode,
              p.product_name as productName,
              l.biz_type as bizType,
              l.biz_no as bizNo,
              l.change_qty as changeQty,
              l.after_stock_qty as afterStockQty,
              l.create_time as createTime
            from wms_stock_log l
            left join base_warehouse w on w.id = l.warehouse_id
            left join base_product p on p.id = l.product_id
            where (:warehouseId is null or l.warehouse_id = :warehouseId)
              and (:productId is null or l.product_id = :productId)
              and (:startTime is null or l.create_time >= :startTime)
              and (:endTime is null or l.create_time <= :endTime)
              and (:kw is null or :kw = ''
                   or lower(l.biz_no) like lower(concat('%', :kw, '%'))
                   or lower(l.biz_type) like lower(concat('%', :kw, '%')))
            order by l.id desc
            """,
            countQuery = """
            select count(*)
            from wms_stock_log l
            where (:warehouseId is null or l.warehouse_id = :warehouseId)
              and (:productId is null or l.product_id = :productId)
              and (:startTime is null or l.create_time >= :startTime)
              and (:endTime is null or l.create_time <= :endTime)
              and (:kw is null or :kw = ''
                   or lower(l.biz_no) like lower(concat('%', :kw, '%'))
                   or lower(l.biz_type) like lower(concat('%', :kw, '%')))
            """,
            nativeQuery = true)
    Page<StockLogRow> pageRows(@Param("kw") String keyword, @Param("warehouseId") Long warehouseId,
            @Param("productId") Long productId,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime,
            Pageable pageable);

    interface StockLogRow {
        Long getId();

        Long getWarehouseId();

        String getWarehouseName();

        Long getProductId();

        String getProductCode();

        String getProductName();

        String getBizType();

        String getBizNo();

        BigDecimal getChangeQty();

        BigDecimal getAfterStockQty();

        LocalDateTime getCreateTime();
    }
}
