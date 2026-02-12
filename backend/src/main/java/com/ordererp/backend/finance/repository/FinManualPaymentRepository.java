package com.ordererp.backend.finance.repository;

import com.ordererp.backend.finance.entity.FinManualPayment;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FinManualPaymentRepository extends JpaRepository<FinManualPayment, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from FinManualPayment m where m.id = :id")
    Optional<FinManualPayment> findByIdForUpdate(@Param("id") Long id);
}

