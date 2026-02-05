package com.ordererp.backend.wms.repository;

import com.ordererp.backend.wms.entity.WmsStockQc;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WmsStockQcRepository extends JpaRepository<WmsStockQc, Long> {
    Optional<WmsStockQc> findFirstByWarehouseIdAndProductId(Long warehouseId, Long productId);
}

