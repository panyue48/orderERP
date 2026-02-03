package com.ordererp.backend.system.repository;

import com.ordererp.backend.system.entity.SysUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SysUserRepository extends JpaRepository<SysUser, Long> {
    Optional<SysUser> findByUsernameAndDeleted(String username, Integer deleted);

    boolean existsByUsernameAndDeleted(String username, Integer deleted);

    @Query("""
            select u from SysUser u
            where u.deleted = 0
              and (:keyword is null or :keyword = ''
                   or lower(u.username) like lower(concat('%', :keyword, '%'))
                   or lower(u.nickname) like lower(concat('%', :keyword, '%')))
            order by u.id desc
            """)
    Page<SysUser> search(@Param("keyword") String keyword, Pageable pageable);
}
