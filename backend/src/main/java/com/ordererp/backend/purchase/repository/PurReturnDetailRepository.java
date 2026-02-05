package com.ordererp.backend.purchase.repository;

import com.ordererp.backend.purchase.entity.PurReturnDetail;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurReturnDetailRepository extends JpaRepository<PurReturnDetail, Long> {
    List<PurReturnDetail> findByReturnIdOrderByIdAsc(Long returnId);
}

