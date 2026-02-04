package com.ordererp.backend.purchase.repository;

import com.ordererp.backend.purchase.entity.PurOrderDetail;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurOrderDetailRepository extends JpaRepository<PurOrderDetail, Long> {
    List<PurOrderDetail> findByOrderIdOrderByIdAsc(Long orderId);
}

