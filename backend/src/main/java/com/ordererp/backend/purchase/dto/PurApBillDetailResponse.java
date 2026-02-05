package com.ordererp.backend.purchase.dto;

import java.util.List;

public record PurApBillDetailResponse(
        PurApBillResponse bill,
        List<PurApBillDocResponse> docs,
        List<PurApPaymentResponse> payments,
        List<PurApInvoiceResponse> invoices) {
}

