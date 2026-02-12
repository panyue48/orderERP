package com.ordererp.backend.finance.repository;

import com.ordererp.backend.finance.entity.FinTransfer;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FinTransferRepository extends JpaRepository<FinTransfer, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from FinTransfer t where t.id = :id")
    Optional<FinTransfer> findByIdForUpdate(@Param("id") Long id);
}

