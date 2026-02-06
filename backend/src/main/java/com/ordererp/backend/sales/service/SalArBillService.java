package com.ordererp.backend.sales.service;

import com.ordererp.backend.base.entity.BasePartner;
import com.ordererp.backend.base.repository.BasePartnerRepository;
import com.ordererp.backend.sales.dto.SalArBillCreateRequest;
import com.ordererp.backend.sales.dto.SalArBillDetailResponse;
import com.ordererp.backend.sales.dto.SalArBillDocResponse;
import com.ordererp.backend.sales.dto.SalArBillResponse;
import com.ordererp.backend.sales.dto.SalArInvoiceCreateRequest;
import com.ordererp.backend.sales.dto.SalArInvoiceResponse;
import com.ordererp.backend.sales.dto.SalArReceiptCreateRequest;
import com.ordererp.backend.sales.dto.SalArReceiptResponse;
import com.ordererp.backend.sales.entity.SalArBill;
import com.ordererp.backend.sales.entity.SalArBillDetail;
import com.ordererp.backend.sales.entity.SalArDocRef;
import com.ordererp.backend.sales.entity.SalArInvoice;
import com.ordererp.backend.sales.entity.SalArReceipt;
import com.ordererp.backend.sales.repository.SalArBillDetailRepository;
import com.ordererp.backend.sales.repository.SalArBillRepository;
import com.ordererp.backend.sales.repository.SalArDocRefRepository;
import com.ordererp.backend.sales.repository.SalArInvoiceRepository;
import com.ordererp.backend.sales.repository.SalArReceiptRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SalArBillService {
    private static final int PARTNER_TYPE_CUSTOMER = 2;

    private static final int STATUS_DRAFT = 1;
    private static final int STATUS_AUDITED = 2;
    private static final int STATUS_PARTIAL_RECEIVED = 3;
    private static final int STATUS_RECEIVED = 4;
    private static final int STATUS_CANCELED = 9;

    private static final int DOC_TYPE_SHIP = 1;
    private static final int DOC_TYPE_RETURN = 2;

    private static final int RECEIPT_STATUS_COMPLETED = 2;
    private static final int RECEIPT_STATUS_CANCELED = 9;

    private static final int INVOICE_STATUS_VALID = 2;
    private static final int INVOICE_STATUS_CANCELED = 9;

    private final SalArBillRepository billRepository;
    private final SalArBillDetailRepository billDetailRepository;
    private final SalArDocRefRepository docRefRepository;
    private final SalArReceiptRepository receiptRepository;
    private final SalArInvoiceRepository invoiceRepository;
    private final BasePartnerRepository partnerRepository;

    public SalArBillService(SalArBillRepository billRepository, SalArBillDetailRepository billDetailRepository,
            SalArDocRefRepository docRefRepository, SalArReceiptRepository receiptRepository,
            SalArInvoiceRepository invoiceRepository, BasePartnerRepository partnerRepository) {
        this.billRepository = billRepository;
        this.billDetailRepository = billDetailRepository;
        this.docRefRepository = docRefRepository;
        this.receiptRepository = receiptRepository;
        this.invoiceRepository = invoiceRepository;
        this.partnerRepository = partnerRepository;
    }

    public Page<SalArBillResponse> page(String keyword, Long customerId, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        return billRepository.pageRows(trimToNull(keyword), customerId, startDate, endDate, pageable).map(SalArBillService::toResponse);
    }

    public SalArBillDetailResponse detail(Long id) {
        SalArBillRepository.SalArBillRow row = billRepository.getRow(id);
        if (row == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "销售对账单不存在");

        List<SalArBillDetailRepository.SalArBillDetailRow> docRows = billDetailRepository.listRows(id);

        List<Long> shipDocIds = new ArrayList<>();
        List<Long> returnDocIds = new ArrayList<>();
        for (var d : docRows) {
            if (d == null || d.getDocType() == null || d.getDocId() == null) continue;
            if (Objects.equals(d.getDocType(), DOC_TYPE_SHIP)) shipDocIds.add(d.getDocId());
            if (Objects.equals(d.getDocType(), DOC_TYPE_RETURN)) returnDocIds.add(d.getDocId());
        }

        Map<Long, String> shipSummaryByDocId = new HashMap<>();
        if (!shipDocIds.isEmpty()) {
            Map<Long, List<SalArBillDetailRepository.ShipDocItemRow>> grouped = new HashMap<>();
            for (var r : billDetailRepository.shipDocItems(shipDocIds)) {
                if (r == null || r.getDocId() == null) continue;
                grouped.computeIfAbsent(r.getDocId(), k -> new ArrayList<>()).add(r);
            }
            for (var e : grouped.entrySet()) {
                shipSummaryByDocId.put(e.getKey(), buildItemSummaryShip(e.getValue()));
            }
        }

        Map<Long, String> returnSummaryByDocId = new HashMap<>();
        if (!returnDocIds.isEmpty()) {
            Map<Long, List<SalArBillDetailRepository.ReturnDocItemRow>> grouped = new HashMap<>();
            for (var r : billDetailRepository.returnDocItems(returnDocIds)) {
                if (r == null || r.getDocId() == null) continue;
                grouped.computeIfAbsent(r.getDocId(), k -> new ArrayList<>()).add(r);
            }
            for (var e : grouped.entrySet()) {
                returnSummaryByDocId.put(e.getKey(), buildItemSummaryReturn(e.getValue()));
            }
        }

        List<SalArBillDocResponse> docs = docRows.stream()
                .map(d -> new SalArBillDocResponse(
                        d.getId(),
                        d.getDocType(),
                        d.getDocId(),
                        d.getDocNo(),
                        d.getOrderId(),
                        d.getOrderNo(),
                        d.getDocTime(),
                        d.getAmount(),
                        Objects.equals(d.getDocType(), DOC_TYPE_SHIP) ? shipSummaryByDocId.get(d.getDocId())
                                : Objects.equals(d.getDocType(), DOC_TYPE_RETURN) ? returnSummaryByDocId.get(d.getDocId()) : null))
                .toList();

        List<SalArReceiptResponse> receipts = receiptRepository.listRows(id).stream()
                .map(r -> new SalArReceiptResponse(
                        r.getId(),
                        r.getReceiptNo(),
                        r.getBillId(),
                        r.getCustomerId(),
                        r.getReceiptDate(),
                        r.getAmount(),
                        r.getMethod(),
                        r.getRemark(),
                        r.getStatus(),
                        r.getCreateBy(),
                        r.getCreateTime(),
                        r.getCancelBy(),
                        r.getCancelTime()))
                .toList();

        List<SalArInvoiceResponse> invoices = invoiceRepository.listRows(id).stream()
                .map(i -> new SalArInvoiceResponse(
                        i.getId(),
                        i.getInvoiceNo(),
                        i.getBillId(),
                        i.getCustomerId(),
                        i.getInvoiceDate(),
                        i.getAmount(),
                        i.getTaxAmount(),
                        i.getRemark(),
                        i.getStatus(),
                        i.getCreateBy(),
                        i.getCreateTime(),
                        i.getCancelBy(),
                        i.getCancelTime()))
                .toList();

        return new SalArBillDetailResponse(toResponse(row), docs, receipts, invoices);
    }

    @Transactional
    public SalArBillResponse create(SalArBillCreateRequest request, String operator) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        if (request.customerId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "customerId is required");
        if (request.startDate() == null || request.endDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate/endDate is required");
        }
        if (request.endDate().isBefore(request.startDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endDate must be >= startDate");
        }

        BasePartner customer = partnerRepository.findByIdAndDeleted(request.customerId(), 0)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "客户不存在"));
        validateCustomer(customer);

        LocalDateTime startTime = request.startDate().atStartOfDay();
        LocalDateTime endTime = request.endDate().plusDays(1).atStartOfDay();

        List<SalArBillRepository.CandidateShipRow> ships = billRepository.candidateShips(request.customerId(), startTime, endTime);
        List<SalArBillRepository.CandidateReturnRow> returns = billRepository.candidateReturns(request.customerId(), startTime, endTime);
        if ((ships == null || ships.isEmpty()) && (returns == null || returns.isEmpty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该范围内没有可对账的发货/退货单据");
        }

        LocalDateTime now = LocalDateTime.now();
        SalArBill bill = new SalArBill();
        bill.setBillNo(generateBillNo());
        bill.setCustomerId(request.customerId());
        bill.setStartDate(request.startDate());
        bill.setEndDate(request.endDate());
        bill.setTotalAmount(BigDecimal.ZERO);
        bill.setReceivedAmount(BigDecimal.ZERO);
        bill.setInvoiceAmount(BigDecimal.ZERO);
        bill.setStatus(STATUS_DRAFT);
        bill.setRemark(trimToNull(request.remark()));
        bill.setCreateBy(trimToNull(operator));
        bill.setCreateTime(now);
        bill = billRepository.saveAndFlush(bill);

        BigDecimal total = BigDecimal.ZERO;
        try {
            for (var r : ships) {
                BigDecimal amt = safeMoney(r.getAmount());
                if (amt.compareTo(BigDecimal.ZERO) == 0) continue;

                SalArBillDetail d = new SalArBillDetail();
                d.setBillId(bill.getId());
                d.setDocType(DOC_TYPE_SHIP);
                d.setDocId(r.getDocId());
                d.setDocNo(r.getDocNo());
                d.setOrderId(r.getOrderId());
                d.setOrderNo(r.getOrderNo());
                d.setDocTime(r.getDocTime());
                d.setAmount(amt);
                billDetailRepository.save(d);

                SalArDocRef ref = new SalArDocRef();
                ref.setDocType(DOC_TYPE_SHIP);
                ref.setDocId(r.getDocId());
                ref.setBillId(bill.getId());
                ref.setCreateTime(now);
                docRefRepository.saveAndFlush(ref);

                total = total.add(amt);
            }
            for (var r : returns) {
                BigDecimal amt = safeMoney(r.getAmount());
                if (amt.compareTo(BigDecimal.ZERO) == 0) continue;

                SalArBillDetail d = new SalArBillDetail();
                d.setBillId(bill.getId());
                d.setDocType(DOC_TYPE_RETURN);
                d.setDocId(r.getDocId());
                d.setDocNo(r.getDocNo());
                d.setOrderId(null);
                d.setOrderNo(null);
                d.setDocTime(r.getDocTime());
                d.setAmount(amt);
                billDetailRepository.save(d);

                SalArDocRef ref = new SalArDocRef();
                ref.setDocType(DOC_TYPE_RETURN);
                ref.setDocId(r.getDocId());
                ref.setBillId(bill.getId());
                ref.setCreateTime(now);
                docRefRepository.saveAndFlush(ref);

                total = total.add(amt);
            }
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "部分单据已被其他对账单占用，请刷新后重试");
        }

        bill.setTotalAmount(total);
        billRepository.save(bill);
        return toResponse(billRepository.getRow(bill.getId()));
    }

    @Transactional
    public SalArBillResponse audit(Long id, String operator) {
        SalArBill bill = billRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "销售对账单不存在"));
        if (Objects.equals(bill.getStatus(), STATUS_CANCELED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "销售对账单已作废");
        }
        if (!Objects.equals(bill.getStatus(), STATUS_DRAFT)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "销售对账单状态不允许审核");
        }

        bill.setStatus(STATUS_AUDITED);
        bill.setAuditBy(trimToNull(operator));
        bill.setAuditTime(LocalDateTime.now());
        billRepository.save(bill);

        refreshReceivedInvoiceAndStatus(bill);
        return toResponse(billRepository.getRow(bill.getId()));
    }

    @Transactional
    public SalArBillResponse regenerate(Long id, String operator) {
        SalArBill bill = billRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "销售对账单不存在"));
        if (Objects.equals(bill.getStatus(), STATUS_CANCELED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "销售对账单已作废");
        }
        if (!Objects.equals(bill.getStatus(), STATUS_DRAFT)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅草稿允许重新生成");
        }

        BigDecimal received = safeMoney(bill.getReceivedAmount());
        BigDecimal invoiced = safeMoney(bill.getInvoiceAmount());
        if (received.compareTo(BigDecimal.ZERO) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "对账单已存在收款记录，不允许重新生成");
        }
        if (invoiced.compareTo(BigDecimal.ZERO) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "对账单已存在发票记录，不允许重新生成");
        }

        billDetailRepository.deleteByBillId(bill.getId());
        docRefRepository.deleteByBillId(bill.getId());

        LocalDateTime startTime = bill.getStartDate().atStartOfDay();
        LocalDateTime endTime = bill.getEndDate().plusDays(1).atStartOfDay();
        List<SalArBillRepository.CandidateShipRow> ships = billRepository.candidateShips(bill.getCustomerId(), startTime, endTime);
        List<SalArBillRepository.CandidateReturnRow> returns = billRepository.candidateReturns(bill.getCustomerId(), startTime, endTime);
        if ((ships == null || ships.isEmpty()) && (returns == null || returns.isEmpty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该范围内没有可对账的发货/退货单据");
        }

        LocalDateTime now = LocalDateTime.now();
        BigDecimal total = BigDecimal.ZERO;
        try {
            for (var r : ships) {
                BigDecimal amt = safeMoney(r.getAmount());
                if (amt.compareTo(BigDecimal.ZERO) == 0) continue;

                SalArBillDetail d = new SalArBillDetail();
                d.setBillId(bill.getId());
                d.setDocType(DOC_TYPE_SHIP);
                d.setDocId(r.getDocId());
                d.setDocNo(r.getDocNo());
                d.setOrderId(r.getOrderId());
                d.setOrderNo(r.getOrderNo());
                d.setDocTime(r.getDocTime());
                d.setAmount(amt);
                billDetailRepository.save(d);

                SalArDocRef ref = new SalArDocRef();
                ref.setDocType(DOC_TYPE_SHIP);
                ref.setDocId(r.getDocId());
                ref.setBillId(bill.getId());
                ref.setCreateTime(now);
                docRefRepository.saveAndFlush(ref);

                total = total.add(amt);
            }
            for (var r : returns) {
                BigDecimal amt = safeMoney(r.getAmount());
                if (amt.compareTo(BigDecimal.ZERO) == 0) continue;

                SalArBillDetail d = new SalArBillDetail();
                d.setBillId(bill.getId());
                d.setDocType(DOC_TYPE_RETURN);
                d.setDocId(r.getDocId());
                d.setDocNo(r.getDocNo());
                d.setOrderId(null);
                d.setOrderNo(null);
                d.setDocTime(r.getDocTime());
                d.setAmount(amt);
                billDetailRepository.save(d);

                SalArDocRef ref = new SalArDocRef();
                ref.setDocType(DOC_TYPE_RETURN);
                ref.setDocId(r.getDocId());
                ref.setBillId(bill.getId());
                ref.setCreateTime(now);
                docRefRepository.saveAndFlush(ref);

                total = total.add(amt);
            }
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "部分单据已被其他对账单占用，请刷新后重试");
        }

        bill.setTotalAmount(total);
        bill.setRemark(mergeRemark(bill.getRemark(), "重新生成：" + trimToNull(operator)));
        billRepository.save(bill);
        return toResponse(billRepository.getRow(bill.getId()));
    }

    @Transactional
    public SalArBillResponse cancel(Long id, String operator) {
        SalArBill bill = billRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "销售对账单不存在"));
        if (Objects.equals(bill.getStatus(), STATUS_CANCELED)) {
            return toResponse(billRepository.getRow(bill.getId()));
        }

        BigDecimal received = safeMoney(bill.getReceivedAmount());
        BigDecimal invoiced = safeMoney(bill.getInvoiceAmount());
        if (received.compareTo(BigDecimal.ZERO) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "对账单已存在收款记录，不能作废");
        }
        if (invoiced.compareTo(BigDecimal.ZERO) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "对账单已存在发票记录，不能作废");
        }

        bill.setStatus(STATUS_CANCELED);
        bill.setRemark(mergeRemark(bill.getRemark(), "作废：" + trimToNull(operator)));
        billRepository.save(bill);

        docRefRepository.deleteByBillId(bill.getId());
        return toResponse(billRepository.getRow(bill.getId()));
    }

    @Transactional
    public SalArReceiptResponse addReceipt(Long billId, SalArReceiptCreateRequest request, String operator) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        BigDecimal amt = safeMoney(request.amount());
        if (amt.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be > 0");
        }

        SalArBill bill = billRepository.findByIdForUpdate(billId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "销售对账单不存在"));
        if (Objects.equals(bill.getStatus(), STATUS_CANCELED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "销售对账单已作废");
        }
        if (!Objects.equals(bill.getStatus(), STATUS_AUDITED)
                && !Objects.equals(bill.getStatus(), STATUS_PARTIAL_RECEIVED)
                && !Objects.equals(bill.getStatus(), STATUS_RECEIVED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "销售对账单未审核，不能登记收款");
        }

        BigDecimal total = safeMoney(bill.getTotalAmount());
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "对账金额<=0，不能登记收款");
        }

        BigDecimal received = safeMoney(bill.getReceivedAmount());
        BigDecimal outstanding = total.subtract(received);
        if (outstanding.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "对账单已结清");
        }
        if (amt.compareTo(outstanding) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "收款金额不能超过未收金额：" + outstanding);
        }

        LocalDateTime now = LocalDateTime.now();
        SalArReceipt r = new SalArReceipt();
        r.setReceiptNo(generateReceiptNo());
        r.setBillId(bill.getId());
        r.setCustomerId(bill.getCustomerId());
        r.setReceiptDate(request.receiptDate() == null ? LocalDate.now() : request.receiptDate());
        r.setAmount(amt);
        r.setMethod(trimToNull(request.method()));
        r.setRemark(trimToNull(request.remark()));
        r.setStatus(RECEIPT_STATUS_COMPLETED);
        r.setCreateBy(trimToNull(operator));
        r.setCreateTime(now);
        r = receiptRepository.saveAndFlush(r);

        bill.setReceivedAmount(received.add(amt));
        billRepository.save(bill);
        refreshReceivedInvoiceAndStatus(bill);

        return toReceiptResponse(r);
    }

    @Transactional
    public SalArReceiptResponse cancelReceipt(Long billId, Long receiptId, String operator) {
        SalArBill bill = billRepository.findByIdForUpdate(billId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "销售对账单不存在"));
        if (Objects.equals(bill.getStatus(), STATUS_CANCELED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "销售对账单已作废");
        }

        SalArReceipt r = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "收款记录不存在"));
        if (!Objects.equals(r.getBillId(), bill.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "收款记录不属于该对账单");
        }
        if (Objects.equals(r.getStatus(), RECEIPT_STATUS_CANCELED)) {
            return toReceiptResponse(r);
        }
        if (!Objects.equals(r.getStatus(), RECEIPT_STATUS_COMPLETED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "收款记录状态不允许作废");
        }

        BigDecimal amt = safeMoney(r.getAmount());
        BigDecimal received = safeMoney(bill.getReceivedAmount());
        if (received.compareTo(amt) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已收金额异常，无法作废该收款记录");
        }

        r.setStatus(RECEIPT_STATUS_CANCELED);
        r.setCancelBy(trimToNull(operator));
        r.setCancelTime(LocalDateTime.now());
        receiptRepository.save(r);

        bill.setReceivedAmount(received.subtract(amt));
        billRepository.save(bill);
        refreshReceivedInvoiceAndStatus(bill);

        return toReceiptResponse(r);
    }

    @Transactional
    public SalArInvoiceResponse addInvoice(Long billId, SalArInvoiceCreateRequest request, String operator) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");

        String invoiceNo = trimToNull(request.invoiceNo());
        if (invoiceNo == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invoiceNo is required");
        }

        BigDecimal amt = safeMoney(request.amount());
        if (amt.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be > 0");
        }

        BigDecimal tax = safeMoney(request.taxAmount());
        if (tax.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "taxAmount must be >= 0");
        }

        SalArBill bill = billRepository.findByIdForUpdate(billId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "销售对账单不存在"));
        if (Objects.equals(bill.getStatus(), STATUS_CANCELED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "销售对账单已作废");
        }
        if (!Objects.equals(bill.getStatus(), STATUS_AUDITED)
                && !Objects.equals(bill.getStatus(), STATUS_PARTIAL_RECEIVED)
                && !Objects.equals(bill.getStatus(), STATUS_RECEIVED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "销售对账单未审核，不能登记发票");
        }

        LocalDateTime now = LocalDateTime.now();
        SalArInvoice i = new SalArInvoice();
        i.setInvoiceNo(invoiceNo);
        i.setBillId(bill.getId());
        i.setCustomerId(bill.getCustomerId());
        i.setInvoiceDate(request.invoiceDate() == null ? LocalDate.now() : request.invoiceDate());
        i.setAmount(amt);
        i.setTaxAmount(tax);
        i.setRemark(trimToNull(request.remark()));
        i.setStatus(INVOICE_STATUS_VALID);
        i.setCreateBy(trimToNull(operator));
        i.setCreateTime(now);
        try {
            i = invoiceRepository.saveAndFlush(i);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "发票号已存在");
        }

        bill.setInvoiceAmount(safeMoney(bill.getInvoiceAmount()).add(amt));
        billRepository.save(bill);

        return toInvoiceResponse(i);
    }

    @Transactional
    public SalArInvoiceResponse cancelInvoice(Long billId, Long invoiceId, String operator) {
        SalArBill bill = billRepository.findByIdForUpdate(billId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "销售对账单不存在"));
        if (Objects.equals(bill.getStatus(), STATUS_CANCELED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "销售对账单已作废");
        }

        SalArInvoice i = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "发票记录不存在"));
        if (!Objects.equals(i.getBillId(), bill.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "发票记录不属于该对账单");
        }
        if (Objects.equals(i.getStatus(), INVOICE_STATUS_CANCELED)) {
            return toInvoiceResponse(i);
        }
        if (!Objects.equals(i.getStatus(), INVOICE_STATUS_VALID)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "发票记录状态不允许作废");
        }

        BigDecimal amt = safeMoney(i.getAmount());
        BigDecimal invoiced = safeMoney(bill.getInvoiceAmount());
        if (invoiced.compareTo(amt) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已开票金额异常，无法作废该发票记录");
        }

        i.setStatus(INVOICE_STATUS_CANCELED);
        i.setCancelBy(trimToNull(operator));
        i.setCancelTime(LocalDateTime.now());
        invoiceRepository.save(i);

        bill.setInvoiceAmount(invoiced.subtract(amt));
        billRepository.save(bill);
        refreshReceivedInvoiceAndStatus(bill);

        return toInvoiceResponse(i);
    }

    private record ItemSummary(String code, String name, BigDecimal qty) {
    }

    private static String buildItemSummaryShip(List<SalArBillDetailRepository.ShipDocItemRow> rows) {
        if (rows == null || rows.isEmpty()) return null;
        List<ItemSummary> items = new ArrayList<>();
        for (var r : rows) {
            if (r == null) continue;
            String code = trimToNull(r.getProductCode());
            String name = trimToNull(r.getProductName());
            if (code == null && name == null) continue;
            items.add(new ItemSummary(code, name, r.getQty()));
        }
        return buildItemSummary(items);
    }

    private static String buildItemSummaryReturn(List<SalArBillDetailRepository.ReturnDocItemRow> rows) {
        if (rows == null || rows.isEmpty()) return null;
        List<ItemSummary> items = new ArrayList<>();
        for (var r : rows) {
            if (r == null) continue;
            String code = trimToNull(r.getProductCode());
            String name = trimToNull(r.getProductName());
            if (code == null && name == null) continue;
            items.add(new ItemSummary(code, name, r.getQty()));
        }
        return buildItemSummary(items);
    }

    private static String buildItemSummary(List<ItemSummary> items) {
        if (items == null || items.isEmpty()) return null;

        int total = items.size();
        int maxShow = 3;
        StringBuilder sb = new StringBuilder();
        int show = Math.min(maxShow, total);
        for (int i = 0; i < show; i++) {
            ItemSummary it = items.get(i);
            if (it == null) continue;
            if (sb.length() > 0) sb.append("，");

            String name = it.name();
            String code = it.code();
            if (name != null && code != null) sb.append(name).append("(").append(code).append(")");
            else if (name != null) sb.append(name);
            else if (code != null) sb.append(code);
            else sb.append("-");

            sb.append("×").append(fmtQty(it.qty()));
        }

        if (total > maxShow) {
            sb.append(" 等").append(total).append("项");
        }

        return sb.toString();
    }

    private static String fmtQty(BigDecimal qty) {
        if (qty == null) return "0";
        BigDecimal q = qty.stripTrailingZeros();
        String s = q.toPlainString();
        return "-0".equals(s) ? "0" : s;
    }

    private static void validateCustomer(BasePartner customer) {
        if (customer == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "客户不存在");
        if (!Objects.equals(customer.getType(), PARTNER_TYPE_CUSTOMER)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "往来单位不是客户");
        }
        if (customer.getStatus() != null && customer.getStatus() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "客户已禁用");
        }
    }

    private static SalArBillResponse toResponse(SalArBillRepository.SalArBillRow row) {
        if (row == null) return null;
        return new SalArBillResponse(
                row.getId(),
                row.getBillNo(),
                row.getCustomerId(),
                row.getCustomerCode(),
                row.getCustomerName(),
                row.getStartDate(),
                row.getEndDate(),
                row.getTotalAmount(),
                row.getReceivedAmount(),
                row.getInvoiceAmount(),
                row.getStatus(),
                row.getRemark(),
                row.getCreateBy(),
                row.getCreateTime(),
                row.getAuditBy(),
                row.getAuditTime());
    }

    private static SalArReceiptResponse toReceiptResponse(SalArReceipt r) {
        return new SalArReceiptResponse(
                r.getId(),
                r.getReceiptNo(),
                r.getBillId(),
                r.getCustomerId(),
                r.getReceiptDate(),
                r.getAmount(),
                r.getMethod(),
                r.getRemark(),
                r.getStatus(),
                r.getCreateBy(),
                r.getCreateTime(),
                r.getCancelBy(),
                r.getCancelTime());
    }

    private static SalArInvoiceResponse toInvoiceResponse(SalArInvoice i) {
        return new SalArInvoiceResponse(
                i.getId(),
                i.getInvoiceNo(),
                i.getBillId(),
                i.getCustomerId(),
                i.getInvoiceDate(),
                i.getAmount(),
                i.getTaxAmount(),
                i.getRemark(),
                i.getStatus(),
                i.getCreateBy(),
                i.getCreateTime(),
                i.getCancelBy(),
                i.getCancelTime());
    }

    private void refreshReceivedInvoiceAndStatus(SalArBill bill) {
        BigDecimal received = safeMoney(receiptRepository.sumCompletedAmount(bill.getId()));
        BigDecimal invoiced = safeMoney(invoiceRepository.sumValidAmount(bill.getId()));
        bill.setReceivedAmount(received);
        bill.setInvoiceAmount(invoiced);

        BigDecimal total = safeMoney(bill.getTotalAmount());
        Integer cur = bill.getStatus();
        if (Objects.equals(cur, STATUS_CANCELED)) {
            billRepository.save(bill);
            return;
        }
        if (Objects.equals(cur, STATUS_DRAFT)) {
            billRepository.save(bill);
            return;
        }

        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            bill.setStatus(STATUS_RECEIVED);
        } else if (received.compareTo(BigDecimal.ZERO) <= 0) {
            bill.setStatus(STATUS_AUDITED);
        } else if (received.compareTo(total) >= 0) {
            bill.setStatus(STATUS_RECEIVED);
        } else {
            bill.setStatus(STATUS_PARTIAL_RECEIVED);
        }
        billRepository.save(bill);
    }

    private static String mergeRemark(String oldRemark, String append) {
        String a = trimToNull(oldRemark);
        String b = trimToNull(append);
        if (b == null) return a;
        if (a == null) return b;
        return a + "；" + b;
    }

    private static BigDecimal safeMoney(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String s = value.trim();
        return s.isEmpty() ? null : s;
    }

    private static String generateBillNo() {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int rand = (int) (Math.random() * 9000) + 1000;
        return "AR" + ts + "-" + rand;
    }

    private static String generateReceiptNo() {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int rand = (int) (Math.random() * 9000) + 1000;
        return "REC" + ts + "-" + rand;
    }
}
