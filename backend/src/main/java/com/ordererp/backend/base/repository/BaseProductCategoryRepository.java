package com.ordererp.backend.base.repository;

import com.ordererp.backend.base.entity.BaseProductCategory;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BaseProductCategoryRepository extends JpaRepository<BaseProductCategory, Long> {
    Optional<BaseProductCategory> findByIdAndDeleted(Long id, Integer deleted);

    Optional<BaseProductCategory> findFirstByCategoryCode(String categoryCode);

    Optional<BaseProductCategory> findByCategoryCodeAndDeleted(String categoryCode, Integer deleted);

    boolean existsByCategoryCodeAndDeleted(String categoryCode, Integer deleted);

    boolean existsByCategoryCodeAndDeletedAndIdNot(String categoryCode, Integer deleted, Long id);

    @Query("""
            select c from BaseProductCategory c
            where (c.deleted is null or c.deleted = 0)
              and (:kw is null or :kw = '' or lower(c.categoryCode) like lower(concat('%', :kw, '%'))
                   or lower(c.categoryName) like lower(concat('%', :kw, '%')))
            order by c.sort asc, c.id asc
            """)
    Page<BaseProductCategory> search(@Param("kw") String keyword, Pageable pageable);

    @Query("""
            select c from BaseProductCategory c
            where (c.deleted is null or c.deleted = 0)
              and (c.status is null or c.status = 1)
              and (:kw is null or :kw = '' or lower(c.categoryCode) like lower(concat('%', :kw, '%'))
                   or lower(c.categoryName) like lower(concat('%', :kw, '%')))
            order by c.sort asc, c.id asc
            """)
    Page<BaseProductCategory> options(@Param("kw") String keyword, Pageable pageable);
}
