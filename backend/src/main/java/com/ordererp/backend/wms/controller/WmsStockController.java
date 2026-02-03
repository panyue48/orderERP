package com.ordererp.backend.wms.controller;

import com.alibaba.excel.EasyExcel;
import com.ordererp.backend.common.dto.PageResponse;
import com.ordererp.backend.common.util.ExcelHttpUtil;
import com.ordererp.backend.wms.dto.WmsStockResponse;
import com.ordererp.backend.wms.excel.WmsStockExcelRow;
import com.ordererp.backend.wms.service.WmsStockService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wms/stocks")
public class WmsStockController {
    private final WmsStockService stockService;

    public WmsStockController(WmsStockService stockService) {
        this.stockService = stockService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('wms:stock:view')")
    public PageResponse<WmsStockResponse> page(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long warehouseId) {
        Pageable pageable = PageRequest.of(page, size);
        Page<WmsStockResponse> res = stockService.page(keyword, warehouseId, pageable);
        return PageResponse.from(res);
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('wms:stock:export')")
    public void export(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long warehouseId,
            HttpServletResponse response) throws IOException {
        ExcelHttpUtil.prepareXlsxResponse(response, "wms-stocks.xlsx");

        int page = 0;
        int size = 2_000;

        try (var writer = EasyExcel.write(response.getOutputStream(), WmsStockExcelRow.class).build()) {
            var sheet = EasyExcel.writerSheet("Stocks").build();
            while (true) {
                Page<WmsStockResponse> res = stockService.page(keyword, warehouseId, PageRequest.of(page, size));
                if (res.isEmpty()) break;

                List<WmsStockExcelRow> rows = res.getContent().stream().map(stockService::toExcelRow).toList();
                writer.write(rows, sheet);
                if (!res.hasNext()) break;
                page++;
            }
        }
    }
}
