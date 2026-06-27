package com.londonmeet.server.repository;

import com.londonmeet.pojo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 用户数据访问接口
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 根据 openid 查询用户
     */
    Optional<User> findByOpenid(String openid);

    Optional<User> findByPublicId(String publicId);

    @Query("""
            select u from User u
            where u.role = 'USER'
              and (:keyword is null or lower(u.nickname) like lower(concat('%', :keyword, '%'))
                   or lower(u.publicId) = lower(:keyword))
              and (:status is null or u.status = :status)
            """)
    Page<User> findAdminUsers(
            @Param("keyword") String keyword,
            @Param("status") String status,
            Pageable pageable
    );
}
