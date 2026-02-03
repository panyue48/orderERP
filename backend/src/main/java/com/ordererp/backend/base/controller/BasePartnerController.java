package com.ordererp.backend.base.controller;

import com.ordererp.backend.base.dto.PartnerCreateRequest;
import com.ordererp.backend.base.dto.PartnerResponse;
import com.ordererp.backend.base.dto.PartnerUpdateRequest;
import com.ordererp.backend.base.service.BasePartnerService;
import com.ordererp.backend.base.service.BaseExcelService;
import com.ordererp.backend.common.dto.PageResponse;
import com.ordererp.backend.common.dto.ImportResult;
import com.ordererp.backend.common.util.ExcelHttpUtil;
import com.ordererp.backend.base.excel.PartnerExcelRow;
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
import com.ordererp.backend.base.dto.PartnerOptionResponse;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestPart;

@RestController
@RequestMapping("/api/base/partners")
public class BasePartnerController {
    private final BasePartnerService partnerService;
    private final BaseExcelService excelService;

    public BasePartnerController(BasePartnerService partnerService, BaseExcelService excelService) {
        this.partnerService = partnerService;
        this.excelService = excelService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('base:partner:view')")
    public PageResponse<PartnerResponse> page(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        Pageable pageable = PageRequest.of(page, size);
        Page<PartnerResponse> result = partnerService.page(keyword, pageable);
        return PageResponse.from(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('base:partner:view')")
    public PartnerResponse get(@PathVariable Long id) {
        return partnerService.get(id);
    }

    @GetMapping("/options")
    @PreAuthorize("hasAuthority('base:partner:view')")
    public List<PartnerOptionResponse> options(@RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "50") int limit) {
        return partnerService.options(keyword, limit);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('base:partner:add')")
    public PartnerResponse create(@Valid @RequestBody PartnerCreateRequest request) {
        return partnerService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('base:partner:edit')")
    public PartnerResponse update(@PathVariable Long id, @Valid @RequestBody PartnerUpdateRequest request) {
        return partnerService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('base:partner:remove')")
    public void delete(@PathVariable Long id) {
        partnerService.delete(id);
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('base:partner:export')")
    public void export(@RequestParam(required = false) String keyword, HttpServletResponse response) throws IOException {
        ExcelHttpUtil.prepareXlsxResponse(response, "partners.xlsx");
        List<PartnerExcelRow> rows = excelService.exportPartners(keyword);
        EasyExcel.write(response.getOutputStream(), PartnerExcelRow.class).sheet("Partners").doWrite(rows);
    }

    @GetMapping("/import-template")
    @PreAuthorize("hasAuthority('base:partner:export')")
    public void importTemplate(HttpServletResponse response) throws IOException {
        ExcelHttpUtil.prepareXlsxResponse(response, "partners-import-template.xlsx");
        PartnerExcelRow sample = new PartnerExcelRow();
        sample.setPartnerCode("SUP-001");
        sample.setPartnerName("示例供应商");
        sample.setType(1);
        sample.setContact("张三");
        sample.setPhone("13800000000");
        sample.setEmail(null);
        sample.setCreditLimit(null);
        sample.setStatus(1);
        EasyExcel.write(response.getOutputStream(), PartnerExcelRow.class).sheet("Template").doWrite(List.of(sample));
    }

    @PostMapping("/import")
    @PreAuthorize("hasAuthority('base:partner:import')")
    public ImportResult importExcel(@RequestPart("file") MultipartFile file) throws IOException {
        List<PartnerExcelRow> rows = EasyExcel.read(file.getInputStream()).head(PartnerExcelRow.class).sheet().doReadSync();
        return excelService.importPartners(rows);
    }
}
