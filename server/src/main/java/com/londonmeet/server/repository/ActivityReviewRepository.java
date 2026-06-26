package com.londonmeet.server.repository;

import com.londonmeet.pojo.entity.ActivityReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import java.util.Collection;
import java.time.LocalDateTime;

public interface ActivityReviewRepository extends JpaRepository<ActivityReview, Long> {

    boolean existsByReviewerUserIdAndActivityIdAndTargetTypeAndTargetId(
            Long reviewerUserId,
            Long activityId,
            String targetType,
            Long targetId
    );

    Optional<ActivityReview> findByReviewerUserIdAndActivityIdAndTargetTypeAndTargetId(
            Long reviewerUserId,
            Long activityId,
            String targetType,
            Long targetId
    );

    int countByTargetTypeAndTargetId(String targetType, Long targetId);

    List<ActivityReview> findByTargetTypeAndTargetId(String targetType, Long targetId);

    int countByTargetTypeAndTargetIdAndStatus(String targetType, Long targetId, String status);

    List<ActivityReview> findByTargetTypeAndTargetIdAndStatus(
            String targetType, Long targetId, String status
    );

    List<ActivityReview> findByActivityIdOrderByCreatedAtDesc(Long activityId);

    List<ActivityReview> findByActivityIdIn(Collection<Long> activityIds);

    @Query("""
            select avg(r.overallScore)
            from ActivityReview r
            join Activity a on a.id = r.activityId
            where r.targetType = :targetType
              and r.status = 'NORMAL'
              and r.createdAt >= :since
              and a.creatorUserId = :creatorUserId
            """)
    Double findRecentAverageActivityRatingByCreatorUserId(
            @Param("creatorUserId") Long creatorUserId,
            @Param("targetType") String targetType,
            @Param("since") LocalDateTime since
    );

    @Query("""
            select r
            from ActivityReview r
            join Activity a on a.id = r.activityId
            where r.targetType = 'activity'
              and r.status = 'NORMAL'
              and r.createdAt >= :since
              and a.creatorUserId = :creatorUserId
            order by r.createdAt desc
            """)
    List<ActivityReview> findRecentActivityReviewsByCreatorUserId(
            @Param("creatorUserId") Long creatorUserId,
            @Param("since") LocalDateTime since
    );

    int countByTargetTypeAndTargetIdAndStatusAndCreatedAtGreaterThanEqual(
            String targetType,
            Long targetId,
            String status,
            LocalDateTime createdAt
    );
}
