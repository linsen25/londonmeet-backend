package com.londonmeet.server.repository;

import com.londonmeet.pojo.entity.UserFeedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UserFeedbackRepository extends JpaRepository<UserFeedback, Long> {
    Page<UserFeedback> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
    Page<UserFeedback> findAllByOrderByCreatedAtDesc(Pageable pageable);
    Page<UserFeedback> findByTypeAndStatusOrderByCreatedAtDesc(String type, String status, Pageable pageable);
    Page<UserFeedback> findByTypeOrderByCreatedAtDesc(String type, Pageable pageable);
    boolean existsByUserIdAndTypeAndStatus(Long userId, String type, String status);
    List<UserFeedback> findByUserIdAndTypeOrderByCreatedAtDesc(Long userId, String type);
}
