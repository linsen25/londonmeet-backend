package com.londonmeet.server.repository;

import com.londonmeet.pojo.entity.ActivityReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;

public interface ActivityReportRepository extends JpaRepository<ActivityReport, Long> {

    boolean existsByReporterUserIdAndActivityId(Long reporterUserId, Long activityId);

    long countByStatus(String status);

    List<ActivityReport> findByActivityIdIn(Collection<Long> activityIds);

    Page<ActivityReport> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

    Page<ActivityReport> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByReportedUserId(Long reportedUserId);
}
