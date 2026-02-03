package com.ordererp.backend.base.service;

import com.ordererp.backend.base.dto.WarehouseCreateRequest;
import com.ordererp.backend.base.dto.WarehouseOptionResponse;
import com.ordererp.backend.base.dto.WarehouseResponse;
import com.ordererp.backend.base.dto.WarehouseUpdateRequest;
import com.ordererp.backend.base.entity.BaseWarehouse;
import com.ordererp.backend.base.repository.BaseWarehouseRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BaseWarehouseService {
    private final BaseWarehouseRepository warehouseRepository;

    public BaseWarehouseService(BaseWarehouseRepository warehouseRepository) {
        this.warehouseRepository = warehouseRepository;
    }

    public Page<WarehouseResponse> page(String keyword, Pageable pageable) {
        return warehouseRepository.search(trimToNull(keyword), pageable).map(BaseWarehouseService::toResponse);
    }

    public WarehouseResponse get(Long id) {
        BaseWarehouse w = warehouseRepository.findByIdAndDeleted(id, 0)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "warehouse not found"));
        return toResponse(w);
    }

    public List<WarehouseOptionResponse> options(String keyword, int limit) {
        int size = Math.min(Math.max(limit, 1), 200);
        Page<BaseWarehouse> page = warehouseRepository.options(trimToNull(keyword), PageRequest.of(0, size));
        return page.getContent().stream()
                .map(w -> new WarehouseOptionResponse(w.getId(), w.getWarehouseCode(), w.getWarehouseName()))
                .toList();
    }

    @Transactional
    public WarehouseResponse create(WarehouseCreateRequest request) {
        String code = normalizeCode(request.warehouseCode());
        BaseWarehouse existing = warehouseRepository.findFirstByWarehouseCode(code).orElse(null);
        if (existing != null && (existing.getDeleted() == null || existing.getDeleted() == 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "warehouseCode already exists");
        }

        BaseWarehouse w = existing != null ? existing : new BaseWarehouse();
        if (w.getId() == null) {
            w.setCreateTime(LocalDateTime.now());
        }
        w.setWarehouseCode(code);
        w.setWarehouseName(normalizeName(request.warehouseName()));
        w.setLocation(trimToNull(request.location()));
        w.setManager(trimToNull(request.manager()));
        w.setStatus(request.status() == null ? 1 : request.status());
        w.setDeleted(0);
        w.setUpdateTime(LocalDateTime.now());
        return toResponse(warehouseRepository.save(w));
    }

    @Transactional
    public WarehouseResponse update(Long id, WarehouseUpdateRequest request) {
        BaseWarehouse w = warehouseRepository.findByIdAndDeleted(id, 0)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "warehouse not found"));

        if (request.warehouseCode() != null) {
            String code = normalizeCode(request.warehouseCode());
            BaseWarehouse other = warehouseRepository.findFirstByWarehouseCode(code).orElse(null);
            if (other != null && other.getId() != null && !other.getId().equals(id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "warehouseCode already exists");
            }
            w.setWarehouseCode(code);
        }
        if (request.warehouseName() != null) {
            w.setWarehouseName(normalizeName(request.warehouseName()));
        }
        if (request.location() != null) {
            w.setLocation(trimToNull(request.location()));
        }
        if (request.manager() != null) {
            w.setManager(trimToNull(request.manager()));
        }
        if (request.status() != null) {
            w.setStatus(request.status());
        }
        w.setUpdateTime(LocalDateTime.now());
        return toResponse(warehouseRepository.save(w));
    }

    @Transactional
    public void delete(Long id) {
        BaseWarehouse w = warehouseRepository.findByIdAndDeleted(id, 0)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "warehouse not found"));
        w.setDeleted(1);
        w.setUpdateTime(LocalDateTime.now());
        warehouseRepository.save(w);
    }

    private static WarehouseResponse toResponse(BaseWarehouse w) {
        return new WarehouseResponse(
                w.getId(),
                w.getWarehouseCode(),
                w.getWarehouseName(),
                w.getLocation(),
                w.getManager(),
                w.getStatus());
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String s = value.trim();
        return s.isEmpty() ? null : s;
    }

    private static String normalizeCode(String code) {
        String s = trimToNull(code);
        if (s == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "warehouseCode is required");
        }
        return s;
    }

    private static String normalizeName(String name) {
        String s = trimToNull(name);
        if (s == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "warehouseName is required");
        }
        return s;
    }
}
