package com.ordererp.backend.base.repository;

import com.ordererp.backend.base.entity.BaseWarehouse;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BaseWarehouseRepository extends JpaRepository<BaseWarehouse, Long> {
    Optional<BaseWarehouse> findByIdAndDeleted(Long id, Integer deleted);

    Optional<BaseWarehouse> findFirstByWarehouseCode(String warehouseCode);

    boolean existsByWarehouseCodeAndDeleted(String warehouseCode, Integer deleted);

    boolean existsByWarehouseCodeAndDeletedAndIdNot(String warehouseCode, Integer deleted, Long id);

    @Query("""
            select w from BaseWarehouse w
            where (w.deleted is null or w.deleted = 0)
              and (:kw is null or :kw = '' or lower(w.warehouseCode) like lower(concat('%', :kw, '%'))
                   or lower(w.warehouseName) like lower(concat('%', :kw, '%')))
            """)
    Page<BaseWarehouse> search(@Param("kw") String keyword, Pageable pageable);

    @Query("""
            select w from BaseWarehouse w
            where (w.deleted is null or w.deleted = 0)
              and (w.status is null or w.status = 1)
              and (:kw is null or :kw = '' or lower(w.warehouseCode) like lower(concat('%', :kw, '%'))
                   or lower(w.warehouseName) like lower(concat('%', :kw, '%')))
            order by w.id asc
            """)
    Page<BaseWarehouse> options(@Param("kw") String keyword, Pageable pageable);
}
