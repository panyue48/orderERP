package com.ordererp.backend.sales.service;

import com.ordererp.backend.base.entity.BasePartner;
import com.ordererp.backend.base.repository.BasePartnerRepository;
import com.ordererp.backend.sales.dto.SalCreditUsageResponse;
import com.ordererp.backend.sales.repository.CustomerAmountRow;
import com.ordererp.backend.sales.repository.SalArBillRepository;
import com.ordererp.backend.sales.repository.SalOrderRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SalCreditService {
    private static final int PARTNER_TYPE_CUSTOMER = 2;

    private final BasePartnerRepository partnerRepository;
    private final SalArBillRepository arBillRepository;
    private final SalOrderRepository orderRepository;

    public SalCreditService(BasePartnerRepository partnerRepository, SalArBillRepository arBillRepository, SalOrderRepository orderRepository) {
        this.partnerRepository = partnerRepository;
        this.arBillRepository = arBillRepository;
        this.orderRepository = orderRepository;
    }

    /**
     * 校验客户信用额度是否允许“新增应收/占用”。
     *
     * <p>口径（用于企业场景更贴近真实）：</p>
     * <ul>
     *   <li>已对账 AR 未收金额：sum(max(total_amount - received_amount, 0))（status in 2/3/4）</li>
     *   <li>未对账发货/退货净额：已发货（未被 AR doc_ref 引用）+ 已执行退货（未被引用，金额为负）</li>
     *   <li>已审核未发完订单剩余金额：sum(price * max(qty - shipped_qty, 0))（status in 2/3）</li>
     * </ul>
     *
     * <p>说明：当客户 credit_limit <= 0 时，视为不启用额度控制（无限额）。</p>
     */
    public void checkIncrease(Long customerId, BigDecimal deltaAmount, String actionName) {
        BasePartner customer = partnerRepository.findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "客户不存在"));
        validateCustomer(customer);

        BigDecimal limit = safeMoney(customer.getCreditLimit());
        if (limit.compareTo(BigDecimal.ZERO) <= 0) return;

        BigDecimal delta = safeMoney(deltaAmount);
        if (delta.compareTo(BigDecimal.ZERO) <= 0) return;

        UsageAmounts usage = computeUsageAmounts(customerId);
        BigDecimal outstandingAr = usage.outstandingAr;
        BigDecimal unbilledShip = usage.unbilledShip;
        BigDecimal unbilledReturn = usage.unbilledReturn;
        BigDecimal openOrders = usage.openOrders;

        BigDecimal used = outstandingAr.add(unbilledShip).add(unbilledReturn).add(openOrders);
        if (used.compareTo(BigDecimal.ZERO) < 0) used = BigDecimal.ZERO;

        BigDecimal projected = used.add(delta);
        if (projected.compareTo(limit) <= 0) return;

        BigDecimal available = limit.subtract(used);
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                (actionName == null ? "操作" : actionName) + "失败：客户信用额度不足。"
                        + " 信用额度=" + fmtMoney(limit)
                        + "，已占用=" + fmtMoney(used)
                        + "（AR欠款=" + fmtMoney(outstandingAr)
                        + "，未对账发货净额=" + fmtMoney(unbilledShip.add(unbilledReturn))
                        + "，未发完订单占用=" + fmtMoney(openOrders) + "）"
                        + "，本次新增=" + fmtMoney(delta)
                        + "，可用=" + fmtMoney(available.max(BigDecimal.ZERO)) + "。");
    }

    public SalCreditUsageResponse getUsage(Long customerId) {
        BasePartner customer = partnerRepository.findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "客户不存在"));
        validateCustomer(customer);
        BigDecimal limit = safeMoney(customer.getCreditLimit());

        UsageAmounts usage = computeUsageAmounts(customerId);
        BigDecimal used = usage.used();
        BigDecimal available = limit.compareTo(BigDecimal.ZERO) > 0 ? limit.subtract(used) : null;
        boolean enabled = limit.compareTo(BigDecimal.ZERO) > 0;

        return new SalCreditUsageResponse(
                customerId,
                enabled ? limit : null,
                used,
                available,
                usage.outstandingAr,
                usage.unbilledShip.add(usage.unbilledReturn),
                usage.openOrders,
                enabled);
    }

    public List<SalCreditUsageResponse> listUsage(List<Long> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) return List.of();
        List<Long> ids = customerIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return List.of();

        Map<Long, BasePartner> partnerById = new HashMap<>();
        for (BasePartner p : partnerRepository.findAllById(ids)) {
            if (p == null || p.getId() == null) continue;
            partnerById.put(p.getId(), p);
        }

        // Bulk sums for performance (partners list UI).
        Map<Long, BigDecimal> arByCustomer = toAmountMap(arBillRepository.sumOutstandingByCustomerIds(ids));
        Map<Long, BigDecimal> shipByCustomer = toAmountMap(arBillRepository.sumUnbilledShipAmountByCustomerIds(ids));
        Map<Long, BigDecimal> retByCustomer = toAmountMap(arBillRepository.sumUnbilledReturnAmountByCustomerIds(ids));
        Map<Long, BigDecimal> openByCustomer = toAmountMap(orderRepository.sumOpenRemainingAmountByCustomerIds(ids));

        List<SalCreditUsageResponse> out = new ArrayList<>();
        for (Long id : ids) {
            BasePartner customer = partnerById.get(id);
            if (customer == null) continue;
            if (!Objects.equals(customer.getType(), PARTNER_TYPE_CUSTOMER)) continue;
            if (customer.getStatus() != null && customer.getStatus() != 1) continue;

            BigDecimal limit = safeMoney(customer.getCreditLimit());
            UsageAmounts usage = new UsageAmounts(
                    safeMoney(arByCustomer.get(id)),
                    safeMoney(shipByCustomer.get(id)),
                    safeMoney(retByCustomer.get(id)),
                    safeMoney(openByCustomer.get(id)));

            BigDecimal used = usage.used();
            boolean enabled = limit.compareTo(BigDecimal.ZERO) > 0;
            BigDecimal available = enabled ? limit.subtract(used) : null;

            out.add(new SalCreditUsageResponse(
                    id,
                    enabled ? limit : null,
                    used,
                    available,
                    usage.outstandingAr,
                    usage.unbilledShip.add(usage.unbilledReturn),
                    usage.openOrders,
                    enabled));
        }
        return out;
    }

    private static void validateCustomer(BasePartner customer) {
        if (!Objects.equals(customer.getType(), PARTNER_TYPE_CUSTOMER)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "往来单位不是客户");
        }
        if (customer.getStatus() != null && customer.getStatus() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "客户已禁用");
        }
    }

    private static BigDecimal safeMoney(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private UsageAmounts computeUsageAmounts(Long customerId) {
        BigDecimal outstandingAr = safeMoney(arBillRepository.sumOutstandingByCustomerId(customerId));
        BigDecimal unbilledShip = safeMoney(arBillRepository.sumUnbilledShipAmountByCustomerId(customerId));
        BigDecimal unbilledReturn = safeMoney(arBillRepository.sumUnbilledReturnAmountByCustomerId(customerId));
        BigDecimal openOrders = safeMoney(orderRepository.sumOpenRemainingAmountByCustomerId(customerId, null));
        return new UsageAmounts(outstandingAr, unbilledShip, unbilledReturn, openOrders);
    }

    private static Map<Long, BigDecimal> toAmountMap(List<? extends CustomerAmountRow> rows) {
        Map<Long, BigDecimal> m = new HashMap<>();
        if (rows == null) return m;
        for (CustomerAmountRow r : rows) {
            if (r == null || r.getCustomerId() == null) continue;
            m.put(r.getCustomerId(), safeMoney(r.getAmount()));
        }
        return m;
    }

    private static String fmtMoney(BigDecimal v) {
        BigDecimal m = safeMoney(v).setScale(2, RoundingMode.HALF_UP);
        return m.toPlainString();
    }

    private record UsageAmounts(BigDecimal outstandingAr, BigDecimal unbilledShip, BigDecimal unbilledReturn, BigDecimal openOrders) {
        BigDecimal used() {
            BigDecimal used = safeMoney(outstandingAr).add(safeMoney(unbilledShip)).add(safeMoney(unbilledReturn)).add(safeMoney(openOrders));
            return used.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : used;
        }
    }
}
