package com.ordererp.backend.base.repository;

import com.ordererp.backend.base.entity.BaseProduct;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BaseProductRepository extends JpaRepository<BaseProduct, Long> {
    Optional<BaseProduct> findByIdAndDeleted(Long id, Integer deleted);

    Optional<BaseProduct> findFirstByProductCode(String productCode);

    boolean existsByProductCodeAndDeleted(String productCode, Integer deleted);

    boolean existsByProductCodeAndDeletedAndIdNot(String productCode, Integer deleted, Long id);

    @Query("""
            select p from BaseProduct p
            where (p.deleted is null or p.deleted = 0)
              and (:kw is null or :kw = '' or lower(p.productCode) like lower(concat('%', :kw, '%'))
                   or lower(p.productName) like lower(concat('%', :kw, '%')))
            """)
    Page<BaseProduct> search(@Param("kw") String keyword, Pageable pageable);

    @Query("""
            select p from BaseProduct p
            where (p.deleted is null or p.deleted = 0)
              and (p.status is null or p.status = 1)
              and (:kw is null or :kw = '' or lower(p.productCode) like lower(concat('%', :kw, '%'))
                   or lower(p.productName) like lower(concat('%', :kw, '%')))
            order by p.id asc
            """)
    Page<BaseProduct> options(@Param("kw") String keyword, Pageable pageable);
}
