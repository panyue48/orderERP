package com.ordererp.backend.base.service;

import com.ordererp.backend.base.entity.BasePartner;
import com.ordererp.backend.base.entity.BaseProduct;
import com.ordererp.backend.base.entity.BaseProductCategory;
import com.ordererp.backend.base.entity.BaseWarehouse;
import com.ordererp.backend.base.excel.CategoryExcelRow;
import com.ordererp.backend.base.excel.PartnerExcelRow;
import com.ordererp.backend.base.excel.ProductExcelRow;
import com.ordererp.backend.base.excel.WarehouseExcelRow;
import com.ordererp.backend.base.repository.BasePartnerRepository;
import com.ordererp.backend.base.repository.BaseProductCategoryRepository;
import com.ordererp.backend.base.repository.BaseProductRepository;
import com.ordererp.backend.base.repository.BaseWarehouseRepository;
import com.ordererp.backend.common.dto.ImportResult;
import com.ordererp.backend.common.dto.ImportResult.RowError;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BaseExcelService {
    private final BaseProductRepository productRepository;
    private final BaseWarehouseRepository warehouseRepository;
    private final BasePartnerRepository partnerRepository;
    private final BaseProductCategoryRepository categoryRepository;

    public BaseExcelService(BaseProductRepository productRepository, BaseWarehouseRepository warehouseRepository,
            BasePartnerRepository partnerRepository, BaseProductCategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.warehouseRepository = warehouseRepository;
        this.partnerRepository = partnerRepository;
        this.categoryRepository = categoryRepository;
    }

    public List<ProductExcelRow> exportProducts(String keyword) {
        // 简化实现：导出所有未删除且匹配 keyword 的数据行。
        List<BaseProduct> items = productRepository.search(trimToNull(keyword),
                org.springframework.data.domain.Pageable.unpaged()).getContent();
        Map<Long, String> categoryIdToCode = loadCategoryIdToCode();

        List<ProductExcelRow> rows = new ArrayList<>();
        for (BaseProduct p : items) {
            ProductExcelRow r = new ProductExcelRow();
            if (p.getCategoryId() != null) {
                r.setCategoryCode(categoryIdToCode.get(p.getCategoryId()));
            }
            r.setProductCode(p.getProductCode());
            r.setProductName(p.getProductName());
            r.setUnit(p.getUnit());
            r.setPurchasePrice(p.getPurchasePrice());
            r.setSalePrice(p.getSalePrice());
            r.setLowStock(p.getLowStock());
            r.setImageUrl(p.getImageUrl());
            r.setStatus(p.getStatus());
            rows.add(r);
        }
        return rows;
    }

    @Transactional
    public ImportResult importProducts(List<ProductExcelRow> rows) {
        List<RowError> errors = new ArrayList<>();
        int inserted = 0;
        int updated = 0;

        Map<String, Long> categoryCodeToId = loadCategoryCodeToId();
        Map<String, Boolean> seen = new HashMap<>();

        int rowNum = 1; // Excel 表头是第 1 行，数据从第 2 行开始；这里用 (rowNum+1) 回报数据行号。
        for (ProductExcelRow r : rows) {
            rowNum++;
            String code = normalizeCode(r.getProductCode());
            if (code == null) {
                errors.add(new RowError(rowNum, "SKU is required"));
                continue;
            }
            String name = normalizeName(r.getProductName());
            if (name == null) {
                errors.add(new RowError(rowNum, "productName is required"));
                continue;
            }
            String key = code.toLowerCase(Locale.ROOT);
            if (seen.putIfAbsent(key, true) != null) {
                errors.add(new RowError(rowNum, "duplicate SKU in file: " + code));
                continue;
            }

            Long categoryId = null;
            String catCode = trimToNull(r.getCategoryCode());
            if (catCode != null) {
                categoryId = categoryCodeToId.get(catCode);
                if (categoryId == null) {
                    errors.add(new RowError(rowNum, "unknown categoryCode: " + catCode));
                    continue;
                }
            }

            BaseProduct p = productRepository.findFirstByProductCode(code).orElseGet(BaseProduct::new);
            boolean isNew = p.getId() == null;
            if (isNew) {
                p.setDeleted(0);
                p.setCreateTime(LocalDateTime.now());
            } else if (p.getDeleted() != null && p.getDeleted() == 1) {
                // 唯一索引不允许重复插入：把逻辑删除的数据当作“复活/更新”。
                p.setDeleted(0);
            }

            p.setCategoryId(categoryId);
            p.setProductCode(code);
            p.setProductName(name);
            p.setUnit(defaultIfBlank(r.getUnit(), "个"));
            p.setPurchasePrice(r.getPurchasePrice());
            p.setSalePrice(r.getSalePrice());
            p.setLowStock(r.getLowStock() == null ? 10 : r.getLowStock());
            p.setImageUrl(trimToNull(r.getImageUrl()));
            p.setStatus(normalizeStatus(r.getStatus()));
            p.setUpdateTime(LocalDateTime.now());

            productRepository.save(p);
            if (isNew) {
                inserted++;
            } else {
                updated++;
            }
        }

        return ImportResult.ok(rows.size(), inserted, updated, errors);
    }

    public List<WarehouseExcelRow> exportWarehouses(String keyword) {
        List<BaseWarehouse> items = warehouseRepository.search(trimToNull(keyword),
                org.springframework.data.domain.Pageable.unpaged()).getContent();
        List<WarehouseExcelRow> rows = new ArrayList<>();
        for (BaseWarehouse w : items) {
            WarehouseExcelRow r = new WarehouseExcelRow();
            r.setWarehouseCode(w.getWarehouseCode());
            r.setWarehouseName(w.getWarehouseName());
            r.setLocation(w.getLocation());
            r.setManager(w.getManager());
            r.setStatus(w.getStatus());
            rows.add(r);
        }
        return rows;
    }

    @Transactional
    public ImportResult importWarehouses(List<WarehouseExcelRow> rows) {
        List<RowError> errors = new ArrayList<>();
        int inserted = 0;
        int updated = 0;
        Map<String, Boolean> seen = new HashMap<>();

        int rowNum = 1;
        for (WarehouseExcelRow r : rows) {
            rowNum++;
            String code = normalizeCode(r.getWarehouseCode());
            String name = normalizeName(r.getWarehouseName());
            if (code == null) {
                errors.add(new RowError(rowNum, "warehouseCode is required"));
                continue;
            }
            if (name == null) {
                errors.add(new RowError(rowNum, "warehouseName is required"));
                continue;
            }
            String key = code.toLowerCase(Locale.ROOT);
            if (seen.putIfAbsent(key, true) != null) {
                errors.add(new RowError(rowNum, "duplicate warehouseCode in file: " + code));
                continue;
            }

            BaseWarehouse w = warehouseRepository.findFirstByWarehouseCode(code).orElseGet(BaseWarehouse::new);
            boolean isNew = w.getId() == null;
            if (isNew) {
                w.setDeleted(0);
                w.setCreateTime(LocalDateTime.now());
            } else if (w.getDeleted() != null && w.getDeleted() == 1) {
                w.setDeleted(0);
            }

            w.setWarehouseCode(code);
            w.setWarehouseName(name);
            w.setLocation(trimToNull(r.getLocation()));
            w.setManager(trimToNull(r.getManager()));
            w.setStatus(normalizeStatus(r.getStatus()));
            w.setUpdateTime(LocalDateTime.now());
            warehouseRepository.save(w);
            if (isNew) inserted++;
            else updated++;
        }

        return ImportResult.ok(rows.size(), inserted, updated, errors);
    }

    public List<PartnerExcelRow> exportPartners(String keyword) {
        List<BasePartner> items = partnerRepository.search(trimToNull(keyword),
                org.springframework.data.domain.Pageable.unpaged()).getContent();
        List<PartnerExcelRow> rows = new ArrayList<>();
        for (BasePartner p : items) {
            PartnerExcelRow r = new PartnerExcelRow();
            r.setPartnerCode(p.getPartnerCode());
            r.setPartnerName(p.getPartnerName());
            r.setType(p.getType());
            r.setContact(p.getContact());
            r.setPhone(p.getPhone());
            r.setEmail(p.getEmail());
            r.setCreditLimit(p.getCreditLimit());
            r.setStatus(p.getStatus());
            rows.add(r);
        }
        return rows;
    }

    @Transactional
    public ImportResult importPartners(List<PartnerExcelRow> rows) {
        List<RowError> errors = new ArrayList<>();
        int inserted = 0;
        int updated = 0;
        Map<String, Boolean> seen = new HashMap<>();

        int rowNum = 1;
        for (PartnerExcelRow r : rows) {
            rowNum++;
            String code = normalizeCode(r.getPartnerCode());
            String name = normalizeName(r.getPartnerName());
            if (code == null) {
                errors.add(new RowError(rowNum, "partnerCode is required"));
                continue;
            }
            if (name == null) {
                errors.add(new RowError(rowNum, "partnerName is required"));
                continue;
            }
            Integer type = r.getType();
            if (type == null || (type != 1 && type != 2)) {
                errors.add(new RowError(rowNum, "type must be 1 or 2"));
                continue;
            }
            String key = code.toLowerCase(Locale.ROOT);
            if (seen.putIfAbsent(key, true) != null) {
                errors.add(new RowError(rowNum, "duplicate partnerCode in file: " + code));
                continue;
            }

            BasePartner p = partnerRepository.findFirstByPartnerCode(code).orElseGet(BasePartner::new);
            boolean isNew = p.getId() == null;
            if (isNew) {
                p.setDeleted(0);
                p.setCreateTime(LocalDateTime.now());
            } else if (p.getDeleted() != null && p.getDeleted() == 1) {
                p.setDeleted(0);
            }

            p.setPartnerCode(code);
            p.setPartnerName(name);
            p.setType(type);
            p.setContact(trimToNull(r.getContact()));
            p.setPhone(trimToNull(r.getPhone()));
            p.setEmail(trimToNull(r.getEmail()));
            p.setCreditLimit(r.getCreditLimit());
            p.setStatus(normalizeStatus(r.getStatus()));
            p.setUpdateTime(LocalDateTime.now());
            partnerRepository.save(p);
            if (isNew) inserted++;
            else updated++;
        }

        return ImportResult.ok(rows.size(), inserted, updated, errors);
    }

    public List<CategoryExcelRow> exportCategories(String keyword) {
        List<BaseProductCategory> items = categoryRepository.search(trimToNull(keyword),
                org.springframework.data.domain.Pageable.unpaged()).getContent();

        Map<Long, String> idToCode = loadCategoryIdToCode();
        List<CategoryExcelRow> rows = new ArrayList<>();
        for (BaseProductCategory c : items) {
            CategoryExcelRow r = new CategoryExcelRow();
            if (c.getParentId() != null && c.getParentId() > 0) {
                r.setParentCategoryCode(idToCode.get(c.getParentId()));
            }
            r.setCategoryCode(c.getCategoryCode());
            r.setCategoryName(c.getCategoryName());
            r.setSort(c.getSort());
            r.setStatus(c.getStatus());
            rows.add(r);
        }
        return rows;
    }

    @Transactional
    public ImportResult importCategories(List<CategoryExcelRow> rows) {
        List<RowError> errors = new ArrayList<>();
        int inserted = 0;
        int updated = 0;
        Map<String, Boolean> seen = new HashMap<>();

        Map<String, Long> codeToId = loadCategoryCodeToId();
        int rowNum = 1;
        for (CategoryExcelRow r : rows) {
            rowNum++;
            String code = normalizeCode(r.getCategoryCode());
            String name = normalizeName(r.getCategoryName());
            if (code == null) {
                errors.add(new RowError(rowNum, "categoryCode is required"));
                continue;
            }
            if (name == null) {
                errors.add(new RowError(rowNum, "categoryName is required"));
                continue;
            }
            String key = code.toLowerCase(Locale.ROOT);
            if (seen.putIfAbsent(key, true) != null) {
                errors.add(new RowError(rowNum, "duplicate categoryCode in file: " + code));
                continue;
            }

            Long parentId = 0L;
            String parentCode = trimToNull(r.getParentCategoryCode());
            if (parentCode != null) {
                Long pid = codeToId.get(parentCode);
                if (pid == null) {
                    errors.add(new RowError(rowNum, "unknown parentCategoryCode: " + parentCode));
                    continue;
                }
                parentId = pid;
            }

            BaseProductCategory c = categoryRepository.findFirstByCategoryCode(code).orElseGet(BaseProductCategory::new);
            boolean isNew = c.getId() == null;
            if (isNew) {
                c.setDeleted(0);
                c.setCreateTime(LocalDateTime.now());
            } else if (c.getDeleted() != null && c.getDeleted() == 1) {
                c.setDeleted(0);
            }
            c.setParentId(parentId);
            c.setCategoryCode(code);
            c.setCategoryName(name);
            c.setSort(r.getSort() == null ? 0 : r.getSort());
            c.setStatus(normalizeStatus(r.getStatus()));
            c.setUpdateTime(LocalDateTime.now());
            categoryRepository.save(c);

            if (isNew) inserted++;
            else updated++;
        }

        return ImportResult.ok(rows.size(), inserted, updated, errors);
    }

    private Map<String, Long> loadCategoryCodeToId() {
        List<BaseProductCategory> categories = categoryRepository.options(null,
                org.springframework.data.domain.PageRequest.of(0, 10000)).getContent();
        Map<String, Long> map = new HashMap<>();
        for (BaseProductCategory c : categories) {
            if (c.getCategoryCode() != null) {
                map.put(c.getCategoryCode(), c.getId());
            }
        }
        return map;
    }

    private Map<Long, String> loadCategoryIdToCode() {
        List<BaseProductCategory> categories = categoryRepository.options(null,
                org.springframework.data.domain.PageRequest.of(0, 10000)).getContent();
        Map<Long, String> map = new HashMap<>();
        for (BaseProductCategory c : categories) {
            map.put(c.getId(), c.getCategoryCode());
        }
        return map;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String s = value.trim();
        return s.isEmpty() ? null : s;
    }

    private static String normalizeCode(String value) {
        String s = trimToNull(value);
        return s == null ? null : s;
    }

    private static String normalizeName(String value) {
        String s = trimToNull(value);
        return s == null ? null : s;
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        String s = trimToNull(value);
        return s == null ? defaultValue : s;
    }

    private static int normalizeStatus(Integer status) {
        if (status == null) return 1;
        return status == 0 ? 0 : 1;
    }
}
