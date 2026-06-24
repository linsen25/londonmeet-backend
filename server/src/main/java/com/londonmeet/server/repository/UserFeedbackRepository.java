package com.londonmeet.server.repository;

import com.londonmeet.pojo.entity.UserFeedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserFeedbackRepository extends JpaRepository<UserFeedback, Long> {
    Page<UserFeedback> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);
    Page<UserFeedback> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
