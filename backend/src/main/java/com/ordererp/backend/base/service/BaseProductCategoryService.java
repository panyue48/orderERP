package com.ordererp.backend.base.service;

import com.ordererp.backend.base.dto.CategoryCreateRequest;
import com.ordererp.backend.base.dto.CategoryOptionResponse;
import com.ordererp.backend.base.dto.CategoryResponse;
import com.ordererp.backend.base.dto.CategoryUpdateRequest;
import com.ordererp.backend.base.entity.BaseProductCategory;
import com.ordererp.backend.base.repository.BaseProductCategoryRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BaseProductCategoryService {
    private final BaseProductCategoryRepository categoryRepository;

    public BaseProductCategoryService(BaseProductCategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    public Page<CategoryResponse> page(String keyword, Pageable pageable) {
        return categoryRepository.search(trimToNull(keyword), pageable).map(BaseProductCategoryService::toResponse);
    }

    public CategoryResponse get(Long id) {
        BaseProductCategory c = categoryRepository.findByIdAndDeleted(id, 0)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "category not found"));
        return toResponse(c);
    }

    public List<CategoryOptionResponse> options(String keyword, int limit) {
        int size = Math.min(Math.max(limit, 1), 200);
        Page<BaseProductCategory> page = categoryRepository.options(trimToNull(keyword), PageRequest.of(0, size));
        return page.getContent().stream()
                .map(c -> new CategoryOptionResponse(c.getId(), normalizeParentId(c.getParentId()), c.getCategoryCode(),
                        c.getCategoryName()))
                .toList();
    }

    @Transactional
    public CategoryResponse create(CategoryCreateRequest request) {
        String code = normalizeCode(request.categoryCode());
        BaseProductCategory existing = categoryRepository.findFirstByCategoryCode(code).orElse(null);
        if (existing != null && (existing.getDeleted() == null || existing.getDeleted() == 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "categoryCode already exists");
        }

        BaseProductCategory c = existing != null ? existing : new BaseProductCategory();
        if (c.getId() == null) {
            c.setCreateTime(LocalDateTime.now());
        }
        c.setParentId(normalizeParentId(request.parentId()));
        c.setCategoryCode(code);
        c.setCategoryName(normalizeName(request.categoryName()));
        c.setSort(request.sort() == null ? 0 : request.sort());
        c.setStatus(request.status() == null ? 1 : request.status());
        c.setDeleted(0);
        c.setUpdateTime(LocalDateTime.now());
        return toResponse(categoryRepository.save(c));
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryUpdateRequest request) {
        BaseProductCategory c = categoryRepository.findByIdAndDeleted(id, 0)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "category not found"));

        if (request.parentId() != null) {
            c.setParentId(normalizeParentId(request.parentId()));
        }
        if (request.categoryCode() != null) {
            String code = normalizeCode(request.categoryCode());
            BaseProductCategory other = categoryRepository.findFirstByCategoryCode(code).orElse(null);
            if (other != null && other.getId() != null && !other.getId().equals(id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "categoryCode already exists");
            }
            c.setCategoryCode(code);
        }
        if (request.categoryName() != null) {
            c.setCategoryName(normalizeName(request.categoryName()));
        }
        if (request.sort() != null) {
            c.setSort(request.sort());
        }
        if (request.status() != null) {
            c.setStatus(request.status());
        }
        c.setUpdateTime(LocalDateTime.now());
        return toResponse(categoryRepository.save(c));
    }

    @Transactional
    public void delete(Long id) {
        BaseProductCategory c = categoryRepository.findByIdAndDeleted(id, 0)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "category not found"));
        c.setDeleted(1);
        c.setUpdateTime(LocalDateTime.now());
        categoryRepository.save(c);
    }

    private static CategoryResponse toResponse(BaseProductCategory c) {
        return new CategoryResponse(
                c.getId(),
                normalizeParentId(c.getParentId()),
                c.getCategoryCode(),
                c.getCategoryName(),
                c.getSort(),
                c.getStatus());
    }

    private static Long normalizeParentId(Long parentId) {
        if (parentId == null || parentId < 0) return 0L;
        return parentId;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String s = value.trim();
        return s.isEmpty() ? null : s;
    }

    private static String normalizeCode(String code) {
        String s = trimToNull(code);
        if (s == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "categoryCode is required");
        }
        return s;
    }

    private static String normalizeName(String name) {
        String s = trimToNull(name);
        if (s == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "categoryName is required");
        }
        return s;
    }
}
