package com.ordererp.backend.purchase.repository;

import com.ordererp.backend.purchase.entity.PurApDocRef;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PurApDocRefRepository extends JpaRepository<PurApDocRef, Long> {
    Optional<PurApDocRef> findFirstByDocTypeAndDocId(Integer docType, Long docId);

    long deleteByBillId(Long billId);
}

