package com.ordererp.backend.base.repository;

import com.ordererp.backend.base.entity.BasePartner;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BasePartnerRepository extends JpaRepository<BasePartner, Long> {
    Optional<BasePartner> findByIdAndDeleted(Long id, Integer deleted);

    Optional<BasePartner> findFirstByPartnerCode(String partnerCode);

    boolean existsByPartnerCodeAndDeleted(String partnerCode, Integer deleted);

    boolean existsByPartnerCodeAndDeletedAndIdNot(String partnerCode, Integer deleted, Long id);

    @Query("""
            select p from BasePartner p
            where (p.deleted is null or p.deleted = 0)
              and (:kw is null or :kw = '' or lower(p.partnerCode) like lower(concat('%', :kw, '%'))
                   or lower(p.partnerName) like lower(concat('%', :kw, '%')))
            """)
    Page<BasePartner> search(@Param("kw") String keyword, Pageable pageable);

    @Query("""
            select p from BasePartner p
            where (p.deleted is null or p.deleted = 0)
              and (p.status is null or p.status = 1)
              and (:kw is null or :kw = '' or lower(p.partnerCode) like lower(concat('%', :kw, '%'))
                   or lower(p.partnerName) like lower(concat('%', :kw, '%')))
            order by p.id asc
            """)
    Page<BasePartner> options(@Param("kw") String keyword, Pageable pageable);
}
