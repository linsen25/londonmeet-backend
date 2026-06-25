package com.londonmeet.server.repository;

import com.londonmeet.pojo.entity.ActivityReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

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

    int countByTargetTypeAndTargetIdAndStatus(String targetType, Long targetId, String status);

    List<ActivityReview> findByTargetTypeAndTargetIdAndStatus(
            String targetType, Long targetId, String status
    );

    @Query("""
            select r
            from ActivityReview r
            join Activity a on a.id = r.activityId
            join User u on u.id = r.reviewerUserId
            where (:targetType is null or r.targetType = :targetType)
              and (:status is null or r.status = :status)
              and (
                    :keyword is null
                    or lower(a.title) like lower(concat('%', :keyword, '%'))
                    or lower(u.nickname) like lower(concat('%', :keyword, '%'))
                  )
            """)
    Page<ActivityReview> findAdminReviews(
            @Param("targetType") String targetType,
            @Param("status") String status,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("""
            select avg(r.overallScore)
            from ActivityReview r
            join Activity a on a.id = r.activityId
            where r.targetType = :targetType
              and r.status = 'NORMAL'
              and a.creatorUserId = :creatorUserId
            """)
    Double findAverageActivityRatingByCreatorUserId(
            @Param("creatorUserId") Long creatorUserId,
            @Param("targetType") String targetType
    );
}
