package com.londonmeet.server.repository;

import com.londonmeet.pojo.entity.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    boolean existsByUserIdAndTypeAndRelatedTypeAndRelatedId(
            Long userId,
            String type,
            String relatedType,
            Long relatedId
    );

    long countByUserIdAndReadAtIsNull(Long userId);

    long countByUserIdAndTypeAndRelatedTypeAndRelatedIdAndCreatedAtAfter(
            Long userId,
            String type,
            String relatedType,
            Long relatedId,
            LocalDateTime createdAt
    );

    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
