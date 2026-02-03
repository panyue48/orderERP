package com.ordererp.backend.base.controller;

import com.ordererp.backend.base.dto.WarehouseCreateRequest;
import com.ordererp.backend.base.dto.WarehouseResponse;
import com.ordererp.backend.base.dto.WarehouseUpdateRequest;
import com.ordererp.backend.base.service.BaseWarehouseService;
import com.ordererp.backend.base.service.BaseExcelService;
import com.ordererp.backend.common.dto.PageResponse;
import com.ordererp.backend.common.dto.ImportResult;
import com.ordererp.backend.common.util.ExcelHttpUtil;
import com.ordererp.backend.base.excel.WarehouseExcelRow;
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
import com.ordererp.backend.base.dto.WarehouseOptionResponse;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestPart;

@RestController
@RequestMapping("/api/base/warehouses")
public class BaseWarehouseController {
    private final BaseWarehouseService warehouseService;
    private final BaseExcelService excelService;

    public BaseWarehouseController(BaseWarehouseService warehouseService, BaseExcelService excelService) {
        this.warehouseService = warehouseService;
        this.excelService = excelService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('base:warehouse:view')")
    public PageResponse<WarehouseResponse> page(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        Pageable pageable = PageRequest.of(page, size);
        Page<WarehouseResponse> result = warehouseService.page(keyword, pageable);
        return PageResponse.from(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('base:warehouse:view')")
    public WarehouseResponse get(@PathVariable Long id) {
        return warehouseService.get(id);
    }

    @GetMapping("/options")
    @PreAuthorize("hasAuthority('base:warehouse:view')")
    public List<WarehouseOptionResponse> options(@RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "50") int limit) {
        return warehouseService.options(keyword, limit);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('base:warehouse:add')")
    public WarehouseResponse create(@Valid @RequestBody WarehouseCreateRequest request) {
        return warehouseService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('base:warehouse:edit')")
    public WarehouseResponse update(@PathVariable Long id, @Valid @RequestBody WarehouseUpdateRequest request) {
        return warehouseService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('base:warehouse:remove')")
    public void delete(@PathVariable Long id) {
        warehouseService.delete(id);
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('base:warehouse:export')")
    public void export(@RequestParam(required = false) String keyword, HttpServletResponse response) throws IOException {
        ExcelHttpUtil.prepareXlsxResponse(response, "warehouses.xlsx");
        List<WarehouseExcelRow> rows = excelService.exportWarehouses(keyword);
        EasyExcel.write(response.getOutputStream(), WarehouseExcelRow.class).sheet("Warehouses").doWrite(rows);
    }

    @GetMapping("/import-template")
    @PreAuthorize("hasAuthority('base:warehouse:export')")
    public void importTemplate(HttpServletResponse response) throws IOException {
        ExcelHttpUtil.prepareXlsxResponse(response, "warehouses-import-template.xlsx");
        WarehouseExcelRow sample = new WarehouseExcelRow();
        sample.setWarehouseCode("WH-01");
        sample.setWarehouseName("主营仓库");
        sample.setLocation(null);
        sample.setManager(null);
        sample.setStatus(1);
        EasyExcel.write(response.getOutputStream(), WarehouseExcelRow.class).sheet("Template").doWrite(List.of(sample));
    }

    @PostMapping("/import")
    @PreAuthorize("hasAuthority('base:warehouse:import')")
    public ImportResult importExcel(@RequestPart("file") MultipartFile file) throws IOException {
        List<WarehouseExcelRow> rows = EasyExcel.read(file.getInputStream()).head(WarehouseExcelRow.class).sheet().doReadSync();
        return excelService.importWarehouses(rows);
    }
}
