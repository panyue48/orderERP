package com.ordererp.backend.purchase.service;

import com.ordererp.backend.base.entity.BasePartner;
import com.ordererp.backend.base.repository.BasePartnerRepository;
import com.ordererp.backend.purchase.dto.PurApBillCreateRequest;
import com.ordererp.backend.purchase.dto.PurApBillDetailResponse;
import com.ordererp.backend.purchase.dto.PurApBillDocResponse;
import com.ordererp.backend.purchase.dto.PurApBillResponse;
import com.ordererp.backend.purchase.dto.PurApInvoiceCreateRequest;
import com.ordererp.backend.purchase.dto.PurApInvoiceResponse;
import com.ordererp.backend.purchase.dto.PurApPaymentCreateRequest;
import com.ordererp.backend.purchase.dto.PurApPaymentResponse;
import com.ordererp.backend.purchase.entity.PurApBill;
import com.ordererp.backend.purchase.entity.PurApBillDetail;
import com.ordererp.backend.purchase.entity.PurApDocRef;
import com.ordererp.backend.purchase.entity.PurApInvoice;
import com.ordererp.backend.purchase.entity.PurApPayment;
import com.ordererp.backend.purchase.repository.PurApBillDetailRepository;
import com.ordererp.backend.purchase.repository.PurApBillRepository;
import com.ordererp.backend.purchase.repository.PurApDocRefRepository;
import com.ordererp.backend.purchase.repository.PurApInvoiceRepository;
import com.ordererp.backend.purchase.repository.PurApPaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PurApBillService {
    private static final int PARTNER_TYPE_SUPPLIER = 1;

    private static final int STATUS_DRAFT = 1;
    private static final int STATUS_AUDITED = 2;
    private static final int STATUS_PARTIAL_PAID = 3;
    private static final int STATUS_PAID = 4;
    private static final int STATUS_CANCELED = 9;

    private static final int DOC_TYPE_INBOUND = 1;
    private static final int DOC_TYPE_RETURN = 2;

    private static final int PAYMENT_STATUS_COMPLETED = 2;
    private static final int PAYMENT_STATUS_CANCELED = 9;

    private static final int INVOICE_STATUS_VALID = 2;
    private static final int INVOICE_STATUS_CANCELED = 9;

    private final PurApBillRepository billRepository;
    private final PurApBillDetailRepository billDetailRepository;
    private final PurApDocRefRepository docRefRepository;
    private final PurApPaymentRepository paymentRepository;
    private final PurApInvoiceRepository invoiceRepository;
    private final BasePartnerRepository partnerRepository;

    public PurApBillService(PurApBillRepository billRepository, PurApBillDetailRepository billDetailRepository,
            PurApDocRefRepository docRefRepository, PurApPaymentRepository paymentRepository,
            PurApInvoiceRepository invoiceRepository, BasePartnerRepository partnerRepository) {
        this.billRepository = billRepository;
        this.billDetailRepository = billDetailRepository;
        this.docRefRepository = docRefRepository;
        this.paymentRepository = paymentRepository;
        this.invoiceRepository = invoiceRepository;
        this.partnerRepository = partnerRepository;
    }

    public Page<PurApBillResponse> page(String keyword, Long supplierId, LocalDate startDate, LocalDate endDate, Pageable pageable) {
        return billRepository.pageRows(trimToNull(keyword), supplierId, startDate, endDate, pageable).map(PurApBillService::toResponse);
    }

    public PurApBillDetailResponse detail(Long id) {
        PurApBillRepository.PurApBillRow row = billRepository.getRow(id);
        if (row == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "对账单不存在");

        List<PurApBillDocResponse> docs = billDetailRepository.listRows(id).stream()
                .map(d -> new PurApBillDocResponse(
                        d.getId(),
                        d.getDocType(),
                        d.getDocId(),
                        d.getDocNo(),
                        d.getOrderId(),
                        d.getOrderNo(),
                        d.getDocTime(),
                        d.getAmount()))
                .toList();

        List<PurApPaymentResponse> payments = paymentRepository.listRows(id).stream()
                .map(p -> new PurApPaymentResponse(
                        p.getId(),
                        p.getPayNo(),
                        p.getBillId(),
                        p.getSupplierId(),
                        p.getPayDate(),
                        p.getAmount(),
                        p.getMethod(),
                        p.getRemark(),
                        p.getStatus(),
                        p.getCreateBy(),
                        p.getCreateTime(),
                        p.getCancelBy(),
                        p.getCancelTime()))
                .toList();

        List<PurApInvoiceResponse> invoices = invoiceRepository.listRows(id).stream()
                .map(i -> new PurApInvoiceResponse(
                        i.getId(),
                        i.getInvoiceNo(),
                        i.getBillId(),
                        i.getSupplierId(),
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

        return new PurApBillDetailResponse(toResponse(row), docs, payments, invoices);
    }

    /**
     * 创建对账单：按供应商 + 日期范围，自动汇总已完成的入库（质检通过）与退货，并锁定这些单据避免重复对账。
     */
    @Transactional
    public PurApBillResponse create(PurApBillCreateRequest request, String operator) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        if (request.supplierId() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "supplierId is required");
        if (request.startDate() == null || request.endDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate/endDate is required");
        }
        if (request.endDate().isBefore(request.startDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "endDate must be >= startDate");
        }

        BasePartner supplier = partnerRepository.findByIdAndDeleted(request.supplierId(), 0)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "供应商不存在"));
        if (!Objects.equals(supplier.getType(), PARTNER_TYPE_SUPPLIER)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "往来单位不是供应商");
        }
        if (supplier.getStatus() != null && supplier.getStatus() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "供应商已禁用");
        }

        LocalDateTime startTime = request.startDate().atStartOfDay();
        LocalDateTime endTime = request.endDate().plusDays(1).atStartOfDay();

        List<PurApBillRepository.CandidateInboundRow> inbounds = billRepository.candidateInbounds(request.supplierId(), startTime, endTime);
        List<PurApBillRepository.CandidateReturnRow> returns = billRepository.candidateReturns(request.supplierId(), startTime, endTime);
        if ((inbounds == null || inbounds.isEmpty()) && (returns == null || returns.isEmpty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该范围内没有可对账的入库/退货单据");
        }

        LocalDateTime now = LocalDateTime.now();
        PurApBill bill = new PurApBill();
        bill.setBillNo(generateBillNo());
        bill.setSupplierId(request.supplierId());
        bill.setStartDate(request.startDate());
        bill.setEndDate(request.endDate());
        bill.setTotalAmount(BigDecimal.ZERO);
        bill.setPaidAmount(BigDecimal.ZERO);
        bill.setInvoiceAmount(BigDecimal.ZERO);
        bill.setStatus(STATUS_DRAFT);
        bill.setRemark(trimToNull(request.remark()));
        bill.setCreateBy(trimToNull(operator));
        bill.setCreateTime(now);
        bill = billRepository.saveAndFlush(bill);

        BigDecimal total = BigDecimal.ZERO;
        try {
            for (PurApBillRepository.CandidateInboundRow r : inbounds) {
                BigDecimal amt = safeMoney(r.getAmount());
                if (amt.compareTo(BigDecimal.ZERO) == 0) continue;

                PurApBillDetail d = new PurApBillDetail();
                d.setBillId(bill.getId());
                d.setDocType(DOC_TYPE_INBOUND);
                d.setDocId(r.getDocId());
                d.setDocNo(r.getDocNo());
                d.setOrderId(r.getOrderId());
                d.setOrderNo(r.getOrderNo());
                d.setDocTime(r.getDocTime());
                d.setAmount(amt);
                billDetailRepository.save(d);

                PurApDocRef ref = new PurApDocRef();
                ref.setDocType(DOC_TYPE_INBOUND);
                ref.setDocId(r.getDocId());
                ref.setBillId(bill.getId());
                ref.setCreateTime(now);
                docRefRepository.saveAndFlush(ref);

                total = total.add(amt);
            }
            for (PurApBillRepository.CandidateReturnRow r : returns) {
                BigDecimal amt = safeMoney(r.getAmount());
                if (amt.compareTo(BigDecimal.ZERO) == 0) continue;

                PurApBillDetail d = new PurApBillDetail();
                d.setBillId(bill.getId());
                d.setDocType(DOC_TYPE_RETURN);
                d.setDocId(r.getDocId());
                d.setDocNo(r.getDocNo());
                d.setOrderId(null);
                d.setOrderNo(null);
                d.setDocTime(r.getDocTime());
                d.setAmount(amt);
                billDetailRepository.save(d);

                PurApDocRef ref = new PurApDocRef();
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
    public PurApBillResponse audit(Long id, String operator) {
        PurApBill bill = billRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "对账单不存在"));
        if (Objects.equals(bill.getStatus(), STATUS_CANCELED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "对账单已作废");
        }
        if (!Objects.equals(bill.getStatus(), STATUS_DRAFT)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "对账单状态不允许审核");
        }

        bill.setStatus(STATUS_AUDITED);
        bill.setAuditBy(trimToNull(operator));
        bill.setAuditTime(LocalDateTime.now());
        billRepository.save(bill);

        refreshPaidInvoiceAndStatus(bill);
        return toResponse(billRepository.getRow(bill.getId()));
    }

    /**
     * 重新生成对账单据：仅允许草稿状态使用，用于刷新“周期内新增/变更”的单据汇总。
     *
     * <p>锁定规则：</p>
     * <ul>
     *   <li>对账单一旦审核（或进入已付/部分已付），单据列表即锁定，不允许再变更。</li>
     *   <li>重新生成仅对草稿生效；且要求未发生付款/开票。</li>
     * </ul>
     */
    @Transactional
    public PurApBillResponse regenerate(Long id, String operator) {
        PurApBill bill = billRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "对账单不存在"));
        if (Objects.equals(bill.getStatus(), STATUS_CANCELED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "对账单已作废");
        }
        if (!Objects.equals(bill.getStatus(), STATUS_DRAFT)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "对账单已锁定（非草稿），不允许重新生成");
        }
        if (safeMoney(bill.getPaidAmount()).compareTo(BigDecimal.ZERO) != 0 || safeMoney(bill.getInvoiceAmount()).compareTo(BigDecimal.ZERO) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "对账单已发生付款/开票，不允许重新生成");
        }

        LocalDateTime startTime = bill.getStartDate().atStartOfDay();
        LocalDateTime endTime = bill.getEndDate().plusDays(1).atStartOfDay();

        // 先释放当前对账单占用的单据，再重新汇总。
        billDetailRepository.deleteByBillId(bill.getId());
        docRefRepository.deleteByBillId(bill.getId());

        List<PurApBillRepository.CandidateInboundRow> inbounds = billRepository.candidateInbounds(bill.getSupplierId(), startTime, endTime);
        List<PurApBillRepository.CandidateReturnRow> returns = billRepository.candidateReturns(bill.getSupplierId(), startTime, endTime);
        if ((inbounds == null || inbounds.isEmpty()) && (returns == null || returns.isEmpty())) {
            bill.setTotalAmount(BigDecimal.ZERO);
            bill.setRemark(mergeRemark(bill.getRemark(), "重新生成：无可对账单据"));
            billRepository.save(bill);
            return toResponse(billRepository.getRow(bill.getId()));
        }

        LocalDateTime now = LocalDateTime.now();
        BigDecimal total = BigDecimal.ZERO;
        try {
            for (PurApBillRepository.CandidateInboundRow r : inbounds) {
                BigDecimal amt = safeMoney(r.getAmount());
                if (amt.compareTo(BigDecimal.ZERO) == 0) continue;

                PurApBillDetail d = new PurApBillDetail();
                d.setBillId(bill.getId());
                d.setDocType(DOC_TYPE_INBOUND);
                d.setDocId(r.getDocId());
                d.setDocNo(r.getDocNo());
                d.setOrderId(r.getOrderId());
                d.setOrderNo(r.getOrderNo());
                d.setDocTime(r.getDocTime());
                d.setAmount(amt);
                billDetailRepository.save(d);

                PurApDocRef ref = new PurApDocRef();
                ref.setDocType(DOC_TYPE_INBOUND);
                ref.setDocId(r.getDocId());
                ref.setBillId(bill.getId());
                ref.setCreateTime(now);
                docRefRepository.saveAndFlush(ref);

                total = total.add(amt);
            }
            for (PurApBillRepository.CandidateReturnRow r : returns) {
                BigDecimal amt = safeMoney(r.getAmount());
                if (amt.compareTo(BigDecimal.ZERO) == 0) continue;

                PurApBillDetail d = new PurApBillDetail();
                d.setBillId(bill.getId());
                d.setDocType(DOC_TYPE_RETURN);
                d.setDocId(r.getDocId());
                d.setDocNo(r.getDocNo());
                d.setOrderId(null);
                d.setOrderNo(null);
                d.setDocTime(r.getDocTime());
                d.setAmount(amt);
                billDetailRepository.save(d);

                PurApDocRef ref = new PurApDocRef();
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
    public PurApBillResponse cancel(Long id, String operator) {
        PurApBill bill = billRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "对账单不存在"));
        if (Objects.equals(bill.getStatus(), STATUS_CANCELED)) {
            return toResponse(billRepository.getRow(bill.getId()));
        }

        BigDecimal paid = safeMoney(bill.getPaidAmount());
        BigDecimal invoiced = safeMoney(bill.getInvoiceAmount());
        if (paid.compareTo(BigDecimal.ZERO) != 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "对账单已存在付款记录，不能作废");
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
    public PurApPaymentResponse addPayment(Long billId, PurApPaymentCreateRequest request, String operator) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        BigDecimal amt = safeMoney(request.amount());
        if (amt.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be > 0");
        }

        PurApBill bill = billRepository.findByIdForUpdate(billId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "对账单不存在"));
        if (Objects.equals(bill.getStatus(), STATUS_CANCELED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "对账单已作废");
        }
        if (!Objects.equals(bill.getStatus(), STATUS_AUDITED)
                && !Objects.equals(bill.getStatus(), STATUS_PARTIAL_PAID)
                && !Objects.equals(bill.getStatus(), STATUS_PAID)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "对账单未审核，不能登记付款");
        }

        BigDecimal total = safeMoney(bill.getTotalAmount());
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "对账金额<=0，不能登记付款");
        }

        BigDecimal paid = safeMoney(bill.getPaidAmount());
        BigDecimal outstanding = total.subtract(paid);
        if (outstanding.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "对账单已结清");
        }
        if (amt.compareTo(outstanding) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "付款金额不能超过未付金额：" + outstanding);
        }

        LocalDateTime now = LocalDateTime.now();
        PurApPayment p = new PurApPayment();
        p.setPayNo(generatePayNo());
        p.setBillId(bill.getId());
        p.setSupplierId(bill.getSupplierId());
        p.setPayDate(request.payDate() == null ? LocalDate.now() : request.payDate());
        p.setAmount(amt);
        p.setMethod(trimToNull(request.method()));
        p.setRemark(trimToNull(request.remark()));
        p.setStatus(PAYMENT_STATUS_COMPLETED);
        p.setCreateBy(trimToNull(operator));
        p.setCreateTime(now);
        p = paymentRepository.saveAndFlush(p);

        bill.setPaidAmount(paid.add(amt));
        billRepository.save(bill);
        refreshPaidInvoiceAndStatus(bill);

        return toPaymentResponse(p);
    }

    @Transactional
    public PurApPaymentResponse cancelPayment(Long billId, Long paymentId, String operator) {
        PurApBill bill = billRepository.findByIdForUpdate(billId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "对账单不存在"));
        if (Objects.equals(bill.getStatus(), STATUS_CANCELED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "对账单已作废");
        }

        PurApPayment p = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "付款记录不存在"));
        if (!Objects.equals(p.getBillId(), bill.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "付款记录不属于该对账单");
        }
        if (Objects.equals(p.getStatus(), PAYMENT_STATUS_CANCELED)) {
            return toPaymentResponse(p);
        }
        if (!Objects.equals(p.getStatus(), PAYMENT_STATUS_COMPLETED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "付款记录状态不允许作废");
        }

        BigDecimal amt = safeMoney(p.getAmount());
        BigDecimal paid = safeMoney(bill.getPaidAmount());
        if (paid.compareTo(amt) < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已付金额异常，无法作废该付款");
        }

        p.setStatus(PAYMENT_STATUS_CANCELED);
        p.setCancelBy(trimToNull(operator));
        p.setCancelTime(LocalDateTime.now());
        paymentRepository.save(p);

        bill.setPaidAmount(paid.subtract(amt));
        billRepository.save(bill);
        refreshPaidInvoiceAndStatus(bill);

        return toPaymentResponse(p);
    }

    @Transactional
    public PurApInvoiceResponse addInvoice(Long billId, PurApInvoiceCreateRequest request, String operator) {
        if (request == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "request body is required");
        String invoiceNo = trimToNull(request.invoiceNo());
        if (invoiceNo == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invoiceNo is required");

        BigDecimal amt = safeMoney(request.amount());
        if (amt.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount must be >= 0");
        }
        BigDecimal tax = safeMoney(request.taxAmount());
        if (tax.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "taxAmount must be >= 0");
        }

        PurApBill bill = billRepository.findByIdForUpdate(billId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "对账单不存在"));
        if (Objects.equals(bill.getStatus(), STATUS_CANCELED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "对账单已作废");
        }
        if (!Objects.equals(bill.getStatus(), STATUS_AUDITED)
                && !Objects.equals(bill.getStatus(), STATUS_PARTIAL_PAID)
                && !Objects.equals(bill.getStatus(), STATUS_PAID)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "对账单未审核，不能登记发票");
        }

        LocalDateTime now = LocalDateTime.now();
        PurApInvoice i = new PurApInvoice();
        i.setInvoiceNo(invoiceNo);
        i.setBillId(bill.getId());
        i.setSupplierId(bill.getSupplierId());
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
    public PurApInvoiceResponse cancelInvoice(Long billId, Long invoiceId, String operator) {
        PurApBill bill = billRepository.findByIdForUpdate(billId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "对账单不存在"));
        if (Objects.equals(bill.getStatus(), STATUS_CANCELED)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "对账单已作废");
        }

        PurApInvoice i = invoiceRepository.findById(invoiceId)
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
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已开票金额异常，无法作废该发票");
        }

        i.setStatus(INVOICE_STATUS_CANCELED);
        i.setCancelBy(trimToNull(operator));
        i.setCancelTime(LocalDateTime.now());
        invoiceRepository.save(i);

        bill.setInvoiceAmount(invoiced.subtract(amt));
        billRepository.save(bill);
        refreshPaidInvoiceAndStatus(bill);

        return toInvoiceResponse(i);
    }

    private void refreshPaidInvoiceAndStatus(PurApBill bill) {
        // Keep derived fields consistent (even if user cancels payments/invoices).
        BigDecimal paid = safeMoney(paymentRepository.sumCompletedAmount(bill.getId()));
        BigDecimal invoiced = safeMoney(invoiceRepository.sumValidAmount(bill.getId()));
        bill.setPaidAmount(paid);
        bill.setInvoiceAmount(invoiced);

        BigDecimal total = safeMoney(bill.getTotalAmount());
        Integer cur = bill.getStatus();
        if (Objects.equals(cur, STATUS_CANCELED)) {
            billRepository.save(bill);
            return;
        }

        // Only audited/paid status transitions. Draft stays draft until audit.
        if (Objects.equals(cur, STATUS_DRAFT)) {
            billRepository.save(bill);
            return;
        }

        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            bill.setStatus(STATUS_PAID);
        } else if (paid.compareTo(BigDecimal.ZERO) <= 0) {
            bill.setStatus(STATUS_AUDITED);
        } else if (paid.compareTo(total) >= 0) {
            bill.setStatus(STATUS_PAID);
        } else {
            bill.setStatus(STATUS_PARTIAL_PAID);
        }
        billRepository.save(bill);
    }

    private static PurApBillResponse toResponse(PurApBillRepository.PurApBillRow row) {
        if (row == null) return null;
        return new PurApBillResponse(
                row.getId(),
                row.getBillNo(),
                row.getSupplierId(),
                row.getSupplierCode(),
                row.getSupplierName(),
                row.getStartDate(),
                row.getEndDate(),
                row.getTotalAmount(),
                row.getPaidAmount(),
                row.getInvoiceAmount(),
                row.getStatus(),
                row.getRemark(),
                row.getCreateBy(),
                row.getCreateTime(),
                row.getAuditBy(),
                row.getAuditTime());
    }

    private static PurApPaymentResponse toPaymentResponse(PurApPayment p) {
        return new PurApPaymentResponse(
                p.getId(),
                p.getPayNo(),
                p.getBillId(),
                p.getSupplierId(),
                p.getPayDate(),
                p.getAmount(),
                p.getMethod(),
                p.getRemark(),
                p.getStatus(),
                p.getCreateBy(),
                p.getCreateTime(),
                p.getCancelBy(),
                p.getCancelTime());
    }

    private static PurApInvoiceResponse toInvoiceResponse(PurApInvoice i) {
        return new PurApInvoiceResponse(
                i.getId(),
                i.getInvoiceNo(),
                i.getBillId(),
                i.getSupplierId(),
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
        return "AP" + ts + "-" + rand;
    }

    private static String generatePayNo() {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int rand = (int) (Math.random() * 9000) + 1000;
        return "PAY" + ts + "-" + rand;
    }
}
