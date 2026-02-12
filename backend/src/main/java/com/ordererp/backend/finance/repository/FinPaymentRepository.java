package com.ordererp.backend.finance.repository;

import com.ordererp.backend.finance.entity.FinPayment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FinPaymentRepository extends JpaRepository<FinPayment, Long> {
    Optional<FinPayment> findFirstByBizTypeAndBizId(Integer bizType, Long bizId);

    @Query(value = """
            select
              p.id as id,
              p.pay_no as payNo,
              p.type as type,
              p.partner_id as partnerId,
              bp.partner_name as partnerName,
              p.account_id as accountId,
              a.account_name as accountName,
              p.amount as amount,
              p.biz_type as bizType,
              p.biz_id as bizId,
              p.biz_no as bizNo,
              p.pay_date as payDate,
              p.method as method,
              p.remark as remark,
              p.status as status,
              p.create_by as createBy,
              p.create_time as createTime,
              p.cancel_by as cancelBy,
              p.cancel_time as cancelTime
            from fin_payment p
            left join fin_account a on a.id = p.account_id
            left join base_partner bp on bp.id = p.partner_id
            where (:type is null or p.type = :type)
              and (:partnerId is null or p.partner_id = :partnerId)
              and (:accountId is null or p.account_id = :accountId)
              and (:startDate is null or p.pay_date >= :startDate)
              and (:endDate is null or p.pay_date <= :endDate)
              and (:kw is null or :kw = ''
                   or lower(p.pay_no) like lower(concat('%', :kw, '%'))
                   or lower(coalesce(p.biz_no, '')) like lower(concat('%', :kw, '%'))
                   or lower(coalesce(bp.partner_name, '')) like lower(concat('%', :kw, '%'))
                   or lower(coalesce(a.account_name, '')) like lower(concat('%', :kw, '%')))
            order by p.id desc
            """,
            countQuery = """
            select count(*)
            from fin_payment p
            left join fin_account a on a.id = p.account_id
            left join base_partner bp on bp.id = p.partner_id
            where (:type is null or p.type = :type)
              and (:partnerId is null or p.partner_id = :partnerId)
              and (:accountId is null or p.account_id = :accountId)
              and (:startDate is null or p.pay_date >= :startDate)
              and (:endDate is null or p.pay_date <= :endDate)
              and (:kw is null or :kw = ''
                   or lower(p.pay_no) like lower(concat('%', :kw, '%'))
                   or lower(coalesce(p.biz_no, '')) like lower(concat('%', :kw, '%'))
                   or lower(coalesce(bp.partner_name, '')) like lower(concat('%', :kw, '%'))
                   or lower(coalesce(a.account_name, '')) like lower(concat('%', :kw, '%')))
            """,
            nativeQuery = true)
    Page<FinPaymentRow> pageRows(@Param("kw") String keyword,
            @Param("type") Integer type,
            @Param("partnerId") Long partnerId,
            @Param("accountId") Long accountId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            Pageable pageable);

    interface FinPaymentRow {
        Long getId();

        String getPayNo();

        Integer getType();

        Long getPartnerId();

        String getPartnerName();

        Long getAccountId();

        String getAccountName();

        BigDecimal getAmount();

        Integer getBizType();

        Long getBizId();

        String getBizNo();

        LocalDate getPayDate();

        String getMethod();

        String getRemark();

        Integer getStatus();

        String getCreateBy();

        LocalDateTime getCreateTime();

        String getCancelBy();

        LocalDateTime getCancelTime();
    }
}

