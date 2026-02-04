package com.ordererp.backend.base.service;

import com.ordererp.backend.base.dto.ProductCreateRequest;
import com.ordererp.backend.base.dto.ProductOptionResponse;
import com.ordererp.backend.base.dto.ProductResponse;
import com.ordererp.backend.base.dto.ProductUpdateRequest;
import com.ordererp.backend.base.entity.BaseProduct;
import com.ordererp.backend.base.repository.BaseProductRepository;
import java.util.List;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BaseProductService {
    private final BaseProductRepository productRepository;

    public BaseProductService(BaseProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Page<ProductResponse> page(String keyword, Pageable pageable) {
        return productRepository.search(trimToNull(keyword), pageable).map(BaseProductService::toResponse);
    }

    public ProductResponse get(Long id) {
        BaseProduct p = productRepository.findByIdAndDeleted(id, 0)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "product not found"));
        return toResponse(p);
    }

    public List<ProductOptionResponse> options(String keyword, int limit) {
        int size = Math.min(Math.max(limit, 1), 200);
        Page<BaseProduct> page = productRepository.options(trimToNull(keyword), PageRequest.of(0, size));
        return page.getContent().stream()
                .map(p -> new ProductOptionResponse(p.getId(), p.getCategoryId(), p.getProductCode(), p.getProductName(),
                        p.getUnit()))
                .toList();
    }

    @Transactional
    public ProductResponse create(ProductCreateRequest request) {
        String code = normalizeCode(request.productCode());
        BaseProduct existing = productRepository.findFirstByProductCode(code).orElse(null);
        if (existing != null && (existing.getDeleted() == null || existing.getDeleted() == 0)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "productCode already exists");
        }

        // 如果存在同 productCode 但被逻辑删除的记录，则“复活”该记录（唯一键不允许直接重新插入）。
        BaseProduct p = existing != null ? existing : new BaseProduct();
        if (p.getId() == null) {
            p.setCreateTime(LocalDateTime.now());
        }
        p.setCategoryId(request.categoryId());
        p.setProductCode(code);
        p.setProductName(normalizeName(request.productName()));
        p.setBarcode(trimToNull(request.barcode()));
        p.setSpec(trimToNull(request.spec()));
        p.setUnit(defaultIfBlank(request.unit(), "个"));
        p.setWeight(request.weight());
        p.setPurchasePrice(request.purchasePrice());
        p.setSalePrice(request.salePrice());
        p.setLowStock(request.lowStock());
        p.setImageUrl(trimToNull(request.imageUrl()));
        p.setStatus(request.status() == null ? 1 : request.status());
        p.setDeleted(0);
        p.setUpdateTime(LocalDateTime.now());

        return toResponse(productRepository.save(p));
    }

    @Transactional
    public ProductResponse update(Long id, ProductUpdateRequest request) {
        BaseProduct p = productRepository.findByIdAndDeleted(id, 0)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "product not found"));

        if (request.categoryId() != null) {
            p.setCategoryId(request.categoryId());
        }
        if (request.productCode() != null) {
            String code = normalizeCode(request.productCode());
            BaseProduct other = productRepository.findFirstByProductCode(code).orElse(null);
            if (other != null && other.getId() != null && !other.getId().equals(id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "productCode already exists");
            }
            p.setProductCode(code);
        }
        if (request.productName() != null) {
            p.setProductName(normalizeName(request.productName()));
        }
        if (request.barcode() != null) {
            p.setBarcode(trimToNull(request.barcode()));
        }
        if (request.spec() != null) {
            p.setSpec(trimToNull(request.spec()));
        }
        if (request.unit() != null) {
            p.setUnit(defaultIfBlank(request.unit(), "个"));
        }
        if (request.weight() != null) {
            p.setWeight(request.weight());
        }
        if (request.purchasePrice() != null) {
            p.setPurchasePrice(request.purchasePrice());
        }
        if (request.salePrice() != null) {
            p.setSalePrice(request.salePrice());
        }
        if (request.lowStock() != null) {
            p.setLowStock(request.lowStock());
        }
        if (request.imageUrl() != null) {
            p.setImageUrl(trimToNull(request.imageUrl()));
        }
        if (request.status() != null) {
            p.setStatus(request.status());
        }
        p.setUpdateTime(LocalDateTime.now());

        return toResponse(productRepository.save(p));
    }

    @Transactional
    public void delete(Long id) {
        BaseProduct p = productRepository.findByIdAndDeleted(id, 0)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "product not found"));
        p.setDeleted(1);
        p.setUpdateTime(LocalDateTime.now());
        productRepository.save(p);
    }

    private static ProductResponse toResponse(BaseProduct p) {
        return new ProductResponse(
                p.getId(),
                p.getCategoryId(),
                p.getProductCode(),
                p.getProductName(),
                p.getBarcode(),
                p.getSpec(),
                p.getUnit(),
                p.getWeight(),
                p.getPurchasePrice(),
                p.getSalePrice(),
                p.getLowStock(),
                p.getImageUrl(),
                p.getStatus());
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String s = value.trim();
        return s.isEmpty() ? null : s;
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        String s = trimToNull(value);
        return s == null ? defaultValue : s;
    }

    private static String normalizeCode(String code) {
        String s = trimToNull(code);
        if (s == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "productCode is required");
        }
        return s;
    }

    private static String normalizeName(String name) {
        String s = trimToNull(name);
        if (s == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "productName is required");
        }
        return s;
    }
}
