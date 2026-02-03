package com.ordererp.backend.base.controller;

import com.ordererp.backend.base.dto.CategoryCreateRequest;
import com.ordererp.backend.base.dto.CategoryOptionResponse;
import com.ordererp.backend.base.dto.CategoryResponse;
import com.ordererp.backend.base.dto.CategoryUpdateRequest;
import com.ordererp.backend.base.excel.CategoryExcelRow;
import com.ordererp.backend.base.service.BaseExcelService;
import com.ordererp.backend.base.service.BaseProductCategoryService;
import com.ordererp.backend.common.dto.PageResponse;
import com.ordererp.backend.common.dto.ImportResult;
import com.ordererp.backend.common.util.ExcelHttpUtil;
import com.alibaba.excel.EasyExcel;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestPart;

@RestController
@RequestMapping("/api/base/categories")
public class BaseProductCategoryController {
    private final BaseProductCategoryService categoryService;
    private final BaseExcelService excelService;

    public BaseProductCategoryController(BaseProductCategoryService categoryService, BaseExcelService excelService) {
        this.categoryService = categoryService;
        this.excelService = excelService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('base:category:view')")
    public PageResponse<CategoryResponse> page(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        Pageable pageable = PageRequest.of(page, size);
        Page<CategoryResponse> result = categoryService.page(keyword, pageable);
        return PageResponse.from(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('base:category:view')")
    public CategoryResponse get(@PathVariable Long id) {
        return categoryService.get(id);
    }

    @GetMapping("/options")
    @PreAuthorize("hasAuthority('base:category:view')")
    public List<CategoryOptionResponse> options(@RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "200") int limit) {
        return categoryService.options(keyword, limit);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('base:category:add')")
    public CategoryResponse create(@Valid @RequestBody CategoryCreateRequest request) {
        return categoryService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('base:category:edit')")
    public CategoryResponse update(@PathVariable Long id, @Valid @RequestBody CategoryUpdateRequest request) {
        return categoryService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('base:category:remove')")
    public void delete(@PathVariable Long id) {
        categoryService.delete(id);
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('base:category:export')")
    public void export(@RequestParam(required = false) String keyword, HttpServletResponse response) throws IOException {
        ExcelHttpUtil.prepareXlsxResponse(response, "categories.xlsx");
        List<CategoryExcelRow> rows = excelService.exportCategories(keyword);
        EasyExcel.write(response.getOutputStream(), CategoryExcelRow.class).sheet("Categories").doWrite(rows);
    }

    @GetMapping("/import-template")
    @PreAuthorize("hasAuthority('base:category:export')")
    public void importTemplate(HttpServletResponse response) throws IOException {
        ExcelHttpUtil.prepareXlsxResponse(response, "categories-import-template.xlsx");
        CategoryExcelRow sample = new CategoryExcelRow();
        sample.setParentCategoryCode(null);
        sample.setCategoryCode("CAT-NEW");
        sample.setCategoryName("示例分类");
        sample.setSort(1);
        sample.setStatus(1);
        EasyExcel.write(response.getOutputStream(), CategoryExcelRow.class).sheet("Template").doWrite(List.of(sample));
    }

    @PostMapping("/import")
    @PreAuthorize("hasAuthority('base:category:import')")
    public ImportResult importExcel(@RequestPart("file") MultipartFile file) throws IOException {
        List<CategoryExcelRow> rows = EasyExcel.read(file.getInputStream()).head(CategoryExcelRow.class).sheet().doReadSync();
        return excelService.importCategories(rows);
    }
}
