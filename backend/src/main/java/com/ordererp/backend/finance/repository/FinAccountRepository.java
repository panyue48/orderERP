package com.ordererp.backend.finance.repository;

import com.ordererp.backend.finance.entity.FinAccount;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FinAccountRepository extends JpaRepository<FinAccount, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from FinAccount a where a.id = :id")
    Optional<FinAccount> findByIdForUpdate(@Param("id") Long id);

    Optional<FinAccount> findFirstByDeletedAndStatusOrderByIdAsc(Integer deleted, Integer status);

    @Query(value = """
            select
              a.id as id,
              a.account_name as accountName,
              a.account_no as accountNo,
              a.balance as balance,
              a.remark as remark,
              a.status as status,
              a.create_by as createBy,
              a.create_time as createTime,
              a.update_by as updateBy,
              a.update_time as updateTime
            from fin_account a
            where a.deleted = 0
              and (:kw is null or :kw = ''
                   or lower(a.account_name) like lower(concat('%', :kw, '%'))
                   or lower(coalesce(a.account_no, '')) like lower(concat('%', :kw, '%')))
            order by a.id desc
            """,
            countQuery = """
            select count(*)
            from fin_account a
            where a.deleted = 0
              and (:kw is null or :kw = ''
                   or lower(a.account_name) like lower(concat('%', :kw, '%'))
                   or lower(coalesce(a.account_no, '')) like lower(concat('%', :kw, '%')))
            """,
            nativeQuery = true)
    Page<FinAccountRow> pageRows(@Param("kw") String keyword, Pageable pageable);

    @Query(value = """
            select
              a.id as id,
              a.account_name as accountName,
              a.balance as balance
            from fin_account a
            where a.deleted = 0
              and a.status = 1
              and (:kw is null or :kw = ''
                   or lower(a.account_name) like lower(concat('%', :kw, '%'))
                   or lower(coalesce(a.account_no, '')) like lower(concat('%', :kw, '%')))
            order by a.id desc
            limit :limit
            """, nativeQuery = true)
    List<FinAccountOptionRow> optionRows(@Param("kw") String keyword, @Param("limit") int limit);

    interface FinAccountRow {
        Long getId();

        String getAccountName();

        String getAccountNo();

        BigDecimal getBalance();

        String getRemark();

        Integer getStatus();

        String getCreateBy();

        LocalDateTime getCreateTime();

        String getUpdateBy();

        LocalDateTime getUpdateTime();
    }

    interface FinAccountOptionRow {
        Long getId();

        String getAccountName();

        BigDecimal getBalance();
    }
}

