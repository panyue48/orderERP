package com.ordererp.backend.ledger;

import com.ordererp.backend.ledger.dto.LedgerEntryCreateRequest;
import com.ordererp.backend.ledger.dto.LedgerEntryResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ledger-entries")
public class LedgerEntryController {
    private final LedgerEntryService service;

    public LedgerEntryController(LedgerEntryService service) {
        this.service = service;
    }

    @GetMapping
    public Page<LedgerEntryResponse> page(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return service.page(pageable).map(LedgerEntryController::toResponse);
    }

    @GetMapping("/{id}")
    public LedgerEntryResponse get(@PathVariable Long id) {
        return toResponse(service.get(id));
    }

    @PostMapping
    public LedgerEntryResponse create(@Valid @RequestBody LedgerEntryCreateRequest request) {
        return toResponse(service.create(request));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    private static LedgerEntryResponse toResponse(LedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.getId(),
                entry.getEntryDate(),
                entry.getAccountCode(),
                entry.getDescription(),
                entry.getDebit(),
                entry.getCredit(),
                entry.getCreatedAt());
    }
}
