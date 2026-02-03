package com.ordererp.backend.system.repository;

import com.ordererp.backend.system.entity.SysRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SysRoleRepository extends JpaRepository<SysRole, Long> {
    boolean existsByRoleKeyAndDeleted(String roleKey, Integer deleted);

    @Query("""
            select r from SysRole r
            where r.deleted = 0
              and (:keyword is null or :keyword = ''
                   or lower(r.roleName) like lower(concat('%', :keyword, '%'))
                   or lower(r.roleKey) like lower(concat('%', :keyword, '%')))
            order by r.sort asc, r.id asc
            """)
    Page<SysRole> search(@Param("keyword") String keyword, Pageable pageable);
}

