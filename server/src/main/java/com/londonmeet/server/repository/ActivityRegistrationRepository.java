package com.londonmeet.server.repository;

import com.londonmeet.pojo.entity.ActivityRegistration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ActivityRegistrationRepository extends JpaRepository<ActivityRegistration, Long> {

    Optional<ActivityRegistration> findByActivityIdAndUserId(Long activityId, Long userId);

    long countByActivityId(Long activityId);

    long countByActivityIdAndStatusIn(Long activityId, Collection<String> statuses);

    List<ActivityRegistration> findByActivityIdAndStatusIn(
            Long activityId,
            Collection<String> statuses
    );

    List<ActivityRegistration> findByActivityIdInAndStatusIn(
            Collection<Long> activityIds,
            Collection<String> statuses
    );

    List<ActivityRegistration> findByActivityIdIn(Collection<Long> activityIds);

    List<ActivityRegistration> findByActivityIdOrderByCreatedAtAsc(Long activityId);

    List<ActivityRegistration> findByActivityIdAndStatusOrderByCreatedAtAsc(Long activityId, String status);

    List<ActivityRegistration> findByUserIdAndStatus(Long userId, String status);

    List<ActivityRegistration> findByActivityIdInAndUserIdAndStatus(
            Collection<Long> activityIds,
            Long userId,
            String status
    );

    List<ActivityRegistration> findByUserIdAndActivityIdIn(Long userId, Collection<Long> activityIds);

    Optional<ActivityRegistration> findFirstByActivityIdAndStatusOrderByCreatedAtAsc(
            Long activityId,
            String status
    );

    long countByUserIdAndStatusIn(Long userId, Collection<String> statuses);

    List<ActivityRegistration> findByUserIdAndStatusIn(Long userId, Collection<String> statuses);

    @Query("""
            select r
            from ActivityRegistration r
            join Activity a on a.id = r.activityId
            where a.creatorUserId = :creatorUserId
              and r.status = :status
            order by r.createdAt asc
            """)
    List<ActivityRegistration> findByCreatorUserIdAndStatusOrderByCreatedAtAsc(
            @Param("creatorUserId") Long creatorUserId,
            @Param("status") String status
    );

    @Query("""
            select count(r) > 0
            from ActivityRegistration r
            join Activity a on a.id = r.activityId
            where r.userId = :userId
              and r.status in :statuses
              and a.startAt < :endAt
              and a.endAt > :startAt
            """)
    boolean existsTimeConflict(
            @Param("userId") Long userId,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt,
            @Param("statuses") Collection<String> statuses
    );

    @Query("""
            select r
            from ActivityRegistration r
            join Activity a on a.id = r.activityId
            where r.status = :status
              and a.startAt <= :now
            """)
    List<ActivityRegistration> findStartedPendingRegistrations(
            @Param("status") String status,
            @Param("now") LocalDateTime now
    );
}
