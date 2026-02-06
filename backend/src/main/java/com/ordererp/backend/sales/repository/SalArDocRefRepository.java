package com.ordererp.backend.sales.repository;

import com.ordererp.backend.sales.entity.SalArDocRef;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalArDocRefRepository extends JpaRepository<SalArDocRef, Long> {
    Optional<SalArDocRef> findFirstByDocTypeAndDocId(Integer docType, Long docId);

    long deleteByBillId(Long billId);
}

