package com.ordererp.backend.base.controller;

import com.ordererp.backend.base.dto.ProductCreateRequest;
import com.ordererp.backend.base.dto.ProductResponse;
import com.ordererp.backend.base.dto.ProductUpdateRequest;
import com.ordererp.backend.base.service.BaseProductService;
import com.ordererp.backend.base.service.BaseExcelService;
import com.ordererp.backend.common.dto.PageResponse;
import com.ordererp.backend.common.dto.ImportResult;
import com.ordererp.backend.common.util.ExcelHttpUtil;
import com.ordererp.backend.base.excel.ProductExcelRow;
import com.alibaba.excel.EasyExcel;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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
import java.util.List;
import com.ordererp.backend.base.dto.ProductOptionResponse;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestPart;

@RestController
@RequestMapping("/api/base/products")
public class BaseProductController {
    private final BaseProductService productService;
    private final BaseExcelService excelService;

    public BaseProductController(BaseProductService productService, BaseExcelService excelService) {
        this.productService = productService;
        this.excelService = excelService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('base:product:view')")
    public PageResponse<ProductResponse> page(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ProductResponse> result = productService.page(keyword, pageable);
        return PageResponse.from(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('base:product:view')")
    public ProductResponse get(@PathVariable Long id) {
        return productService.get(id);
    }

    @GetMapping("/options")
    @PreAuthorize("hasAuthority('base:product:view')")
    public List<ProductOptionResponse> options(@RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "50") int limit) {
        return productService.options(keyword, limit);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('base:product:add')")
    public ProductResponse create(@Valid @RequestBody ProductCreateRequest request) {
        return productService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('base:product:edit')")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductUpdateRequest request) {
        return productService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('base:product:remove')")
    public void delete(@PathVariable Long id) {
        productService.delete(id);
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('base:product:export')")
    public void export(@RequestParam(required = false) String keyword, HttpServletResponse response) throws IOException {
        ExcelHttpUtil.prepareXlsxResponse(response, "products.xlsx");
        List<ProductExcelRow> rows = excelService.exportProducts(keyword);
        EasyExcel.write(response.getOutputStream(), ProductExcelRow.class).sheet("Products").doWrite(rows);
    }

    @GetMapping("/import-template")
    @PreAuthorize("hasAuthority('base:product:export')")
    public void importTemplate(HttpServletResponse response) throws IOException {
        ExcelHttpUtil.prepareXlsxResponse(response, "products-import-template.xlsx");
        ProductExcelRow sample = new ProductExcelRow();
        sample.setCategoryCode("CAT-DEFAULT");
        sample.setProductCode("SKU-001");
        sample.setProductName("示例商品");
        sample.setUnit("个");
        sample.setPurchasePrice(null);
        sample.setSalePrice(null);
        sample.setLowStock(10);
        sample.setImageUrl(null);
        sample.setStatus(1);
        EasyExcel.write(response.getOutputStream(), ProductExcelRow.class).sheet("Template").doWrite(List.of(sample));
    }

    @PostMapping("/import")
    @PreAuthorize("hasAuthority('base:product:import')")
    public ImportResult importExcel(@RequestPart("file") MultipartFile file) throws IOException {
        List<ProductExcelRow> rows = EasyExcel.read(file.getInputStream()).head(ProductExcelRow.class).sheet().doReadSync();
        return excelService.importProducts(rows);
    }
}
