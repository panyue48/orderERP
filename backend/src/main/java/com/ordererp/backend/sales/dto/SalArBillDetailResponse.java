package com.ordererp.backend.sales.dto;

import java.util.List;

public record SalArBillDetailResponse(
        SalArBillResponse bill,
        List<SalArBillDocResponse> docs,
        List<SalArReceiptResponse> receipts,
        List<SalArInvoiceResponse> invoices) {
}

