package com.ordererp.backend.wms.controller;

import com.alibaba.excel.EasyExcel;
import com.ordererp.backend.common.dto.PageResponse;
import com.ordererp.backend.common.util.ExcelHttpUtil;
import com.ordererp.backend.wms.dto.WmsStockLogResponse;
import com.ordererp.backend.wms.excel.WmsStockLogExcelRow;
import com.ordererp.backend.wms.service.WmsStockLogService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wms/stock-logs")
public class WmsStockLogController {
    private final WmsStockLogService stockLogService;

    public WmsStockLogController(WmsStockLogService stockLogService) {
        this.stockLogService = stockLogService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('wms:stocklog:view')")
    public PageResponse<WmsStockLogResponse> page(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        Pageable pageable = PageRequest.of(page, size);
        Page<WmsStockLogResponse> res = stockLogService.page(keyword, warehouseId, productId, startTime, endTime, pageable);
        return PageResponse.from(res);
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('wms:stocklog:export')")
    public void export(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            HttpServletResponse response) throws IOException {
        ExcelHttpUtil.prepareXlsxResponse(response, "wms-stock-logs.xlsx");

        int page = 0;
        int size = 2_000;

        try (var writer = EasyExcel.write(response.getOutputStream(), WmsStockLogExcelRow.class).build()) {
            var sheet = EasyExcel.writerSheet("StockLogs").build();
            while (true) {
                Page<WmsStockLogResponse> res = stockLogService.page(keyword, warehouseId, productId, startTime, endTime,
                        PageRequest.of(page, size));
                if (res.isEmpty()) break;

                List<WmsStockLogExcelRow> rows = res.getContent().stream().map(WmsStockLogService::toExcelRow).toList();
                writer.write(rows, sheet);
                if (!res.hasNext()) break;
                page++;
            }
        }
    }
}
