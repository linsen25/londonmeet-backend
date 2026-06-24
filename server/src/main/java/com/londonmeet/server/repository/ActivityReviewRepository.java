package com.londonmeet.server.repository;

import com.londonmeet.pojo.entity.ActivityReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

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

    @Query("""
            select avg(r.overallScore)
            from ActivityReview r
            join Activity a on a.id = r.activityId
            where r.targetType = :targetType
              and a.creatorUserId = :creatorUserId
            """)
    Double findAverageActivityRatingByCreatorUserId(
            @Param("creatorUserId") Long creatorUserId,
            @Param("targetType") String targetType
    );
}
