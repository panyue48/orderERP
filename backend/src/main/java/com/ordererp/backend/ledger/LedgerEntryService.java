package com.ordererp.backend.ledger;

import com.ordererp.backend.ledger.dto.LedgerEntryCreateRequest;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.lang.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerEntryService {
    private final LedgerEntryRepository repository;

    public LedgerEntryService(LedgerEntryRepository repository) {
        this.repository = repository;
    }

    public Page<LedgerEntry> page(@NonNull Pageable pageable) {
        return repository.findAll(pageable);
    }

    public LedgerEntry get(@NonNull Long id) {
        return repository.findById(id).orElseThrow();
    }

    @Transactional
    public LedgerEntry create(LedgerEntryCreateRequest request) {
        LedgerEntry entry = new LedgerEntry();
        entry.setEntryDate(request.getEntryDate());
        entry.setAccountCode(request.getAccountCode());
        entry.setDescription(request.getDescription());
        entry.setDebit(nonNull(request.getDebit()));
        entry.setCredit(nonNull(request.getCredit()));
        entry.setCreatedAt(Instant.now());
        return repository.save(entry);
    }

    @Transactional
    public void delete(@NonNull Long id) {
        repository.deleteById(id);
    }

    private static BigDecimal nonNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
