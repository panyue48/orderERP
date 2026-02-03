package com.ordererp.backend.ledger.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public class LedgerEntryResponse {
    private Long id;
    private LocalDate entryDate;
    private String accountCode;
    private String description;
    private BigDecimal debit;
    private BigDecimal credit;
    private Instant createdAt;

    public LedgerEntryResponse(Long id, LocalDate entryDate, String accountCode, String description, BigDecimal debit, BigDecimal credit, Instant createdAt) {
        this.id = id;
        this.entryDate = entryDate;
        this.accountCode = accountCode;
        this.description = description;
        this.debit = debit;
        this.credit = credit;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getEntryDate() {
        return entryDate;
    }

    public String getAccountCode() {
        return accountCode;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getDebit() {
        return debit;
    }

    public BigDecimal getCredit() {
        return credit;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

