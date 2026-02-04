package com.ordererp.backend.purchase.repository;

import com.ordererp.backend.purchase.entity.PurInboundDetail;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurInboundDetailRepository extends JpaRepository<PurInboundDetail, Long> {
    List<PurInboundDetail> findByInboundIdOrderByIdAsc(Long inboundId);
}

