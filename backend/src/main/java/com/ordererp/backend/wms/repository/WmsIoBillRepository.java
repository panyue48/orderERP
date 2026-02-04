package com.ordererp.backend.wms.repository;

import com.ordererp.backend.wms.entity.WmsIoBill;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

public interface WmsIoBillRepository extends JpaRepository<WmsIoBill, Long> {
    /**
     * 根据 bizId+type 查询关联单据。
     *
     * <p>用于幂等语义：同一张原单（bizId）在同一 type 下只能生成一张关联单（例如：冲销单）。</p>
     */
    Optional<WmsIoBill> findFirstByBizIdAndType(Long bizId, Integer type);

    /**
     * 根据 bizNo+type 查询关联单据。
     *
     * <p>用于盘点执行等场景：以盘点单号作为 bizNo，把生成的调整出入库单与盘点单建立可追溯关联。</p>
     */
    Optional<WmsIoBill> findFirstByBizNoAndType(String bizNo, Integer type);

    /**
     * 按主键查询并加悲观写锁（for update）。
     *
     * <p>用于执行/冲销等关键写路径，避免并发重复处理同一张单据。</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from WmsIoBill b where b.id = :id")
    Optional<WmsIoBill> findByIdForUpdate(@Param("id") Long id);

    @Query(value = """
            select
              b.id as id,
              b.bill_no as billNo,
              b.warehouse_id as warehouseId,
              w.warehouse_name as warehouseName,
              b.status as status,
              b.remark as remark,
              b.create_by as createBy,
              b.create_time as createTime,
              coalesce((select sum(d.qty) from wms_io_bill_detail d where d.bill_id = b.id), 0) as totalQty
            from wms_io_bill b
            left join base_warehouse w on w.id = b.warehouse_id
            where b.type = :type
              and (:kw is null or :kw = '' or lower(b.bill_no) like lower(concat('%', :kw, '%')))
            order by b.id desc
            """,
            countQuery = """
            select count(*)
            from wms_io_bill b
            where b.type = :type
              and (:kw is null or :kw = '' or lower(b.bill_no) like lower(concat('%', :kw, '%')))
            """,
            nativeQuery = true)
    Page<BillRow> pageBillRows(@Param("type") int type, @Param("kw") String keyword, Pageable pageable);

    @Query(value = """
            select
              b.id as id,
              b.bill_no as billNo,
              b.warehouse_id as warehouseId,
              w.warehouse_name as warehouseName,
              b.status as status,
              b.remark as remark,
              b.create_by as createBy,
              b.create_time as createTime,
              coalesce((select sum(d.qty) from wms_io_bill_detail d where d.bill_id = b.id), 0) as totalQty
            from wms_io_bill b
            left join base_warehouse w on w.id = b.warehouse_id
            where b.id = :id
            """,
            nativeQuery = true)
    BillRow getBillRow(@Param("id") Long id);

    interface BillRow {
        Long getId();

        String getBillNo();

        Long getWarehouseId();

        String getWarehouseName();

        Integer getStatus();

        BigDecimal getTotalQty();

        String getRemark();

        String getCreateBy();

        LocalDateTime getCreateTime();
    }
}
