package com.londonmeet.server.repository;

import com.londonmeet.pojo.entity.Activity;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

public interface ActivityRepository extends JpaRepository<Activity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Activity a where a.id = :id")
    Optional<Activity> findLockedById(@Param("id") Long id);

    Page<Activity> findByStatusAndEndAtAfterAndEndAtBefore(
            String status,
            LocalDateTime endAtAfter,
            LocalDateTime endAtBefore,
            Pageable pageable
    );

    Page<Activity> findByCreatorUserIdAndStatusAndEndAtAfter(
            Long creatorUserId,
            String status,
            LocalDateTime now,
            Pageable pageable
    );

    @Query("""
            select count(a) > 0
            from Activity a
            where a.creatorUserId = :creatorUserId
              and a.status = :status
              and (:excludeId is null or a.id <> :excludeId)
              and a.startAt < :endAt
              and a.endAt > :startAt
            """)
    boolean existsCreatorTimeConflict(
            @Param("creatorUserId") Long creatorUserId,
            @Param("status") String status,
            @Param("startAt") LocalDateTime startAt,
            @Param("endAt") LocalDateTime endAt,
            @Param("excludeId") Long excludeId
    );

    java.util.List<Activity> findByStatusAndEndAtAfterAndQrExpiresAtBetween(
            String status,
            LocalDateTime now,
            LocalDateTime from,
            LocalDateTime to
    );

    java.util.List<Activity> findByStatusAndEndAtBetween(
            String status,
            LocalDateTime from,
            LocalDateTime to
    );

    java.util.List<Activity> findByStatusAndStartAtBetween(
            String status,
            LocalDateTime from,
            LocalDateTime to
    );

    @Query("""
            select a
            from Activity a
            where a.status = :status
              and a.endAt > :now
              and (:keyword is null or lower(a.title) like lower(concat('%', :keyword, '%')))
              and (:hasTagFilter = false or a.tagId in :tagIds)
            """)
    Page<Activity> searchPublishedActivities(
            @Param("status") String status,
            @Param("now") LocalDateTime now,
            @Param("keyword") String keyword,
            @Param("hasTagFilter") boolean hasTagFilter,
            @Param("tagIds") java.util.Collection<Long> tagIds,
            Pageable pageable
    );

    long countByCreatorUserId(Long creatorUserId);

    long countByCreatorUserIdAndStatusAndStartAtLessThanEqualAndEndAtAfter(
            Long creatorUserId,
            String status,
            LocalDateTime startAt,
            LocalDateTime endAt
    );

    @Query("select coalesce(sum(a.likeCount), 0) from Activity a where a.creatorUserId = :creatorUserId")
    long sumLikeCountByCreatorUserId(@Param("creatorUserId") Long creatorUserId);

    long countByStartAtAfter(LocalDateTime now);

    long countByStartAtLessThanEqualAndEndAtAfter(LocalDateTime startAt, LocalDateTime endAt);

    long countByEndAtLessThanEqual(LocalDateTime now);

    long countByCreatedAtBetween(LocalDateTime startAt, LocalDateTime endAt);

    @Query("""
            select a
            from Activity a
            where (:keyword is null or lower(a.title) like lower(concat('%', :keyword, '%'))
                   or lower(a.authorName) like lower(concat('%', :keyword, '%')))
              and (
                    :status is null
                    or (:status = 'upcoming' and a.startAt > :now)
                    or (:status = 'ongoing' and a.startAt <= :now and a.endAt > :now)
                    or (:status = 'ended' and a.endAt <= :now)
                  )
            """)
    Page<Activity> findAdminActivities(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    @Modifying
    @Query("update Activity a set a.authorName = :authorName where a.creatorUserId = :creatorUserId")
    int updateAuthorNameByCreatorUserId(
            @Param("creatorUserId") Long creatorUserId,
            @Param("authorName") String authorName
    );

    @Modifying
    @Query("update Activity a set a.avatarUrl = :avatarUrl where a.creatorUserId = :creatorUserId")
    int updateAvatarUrlByCreatorUserId(
            @Param("creatorUserId") Long creatorUserId,
            @Param("avatarUrl") String avatarUrl
    );

    @Query("""
            select distinct a
            from Activity a
            join ActivityRegistration r on r.activityId = a.id
            where a.status = :status
              and a.endAt > :now
              and a.creatorUserId <> :userId
              and r.userId = :userId
              and r.status in :registrationStatuses
            """)
    Page<Activity> findRelatedOngoingActivities(
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("now") LocalDateTime now,
            @Param("registrationStatuses") Collection<String> registrationStatuses,
            Pageable pageable
    );

    @Query("""
            select a
            from Activity a
            join ActivityFavorite f on f.activityId = a.id
            where f.userId = :userId
              and a.status = :status
              and a.endAt > :now
            order by f.createdAt desc
            """)
    Page<Activity> findActiveFavorites(
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    @Query("""
            select distinct a
            from Activity a
            left join ActivityRegistration r on r.activityId = a.id
            where a.status = :status
              and a.endAt <= :now
              and (
                    a.creatorUserId = :userId
                    or (r.userId = :userId and r.status in :registrationStatuses)
                  )
            order by a.endAt desc
            """)
    java.util.List<Activity> findRelatedEndedActivities(
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("now") LocalDateTime now,
            @Param("registrationStatuses") Collection<String> registrationStatuses
    );

    @Query("""
            select distinct a
            from Activity a
            join ActivityRegistration r on r.activityId = a.id
            where a.status = :status
              and a.endAt <= :now
              and a.creatorUserId <> :userId
              and r.userId = :userId
              and r.status in :registrationStatuses
            order by a.endAt desc
            """)
    java.util.List<Activity> findJoinedEndedActivities(
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("now") LocalDateTime now,
            @Param("registrationStatuses") Collection<String> registrationStatuses
    );

    @Query("""
            select a
            from Activity a
            where a.status = :status
              and a.endAt <= :now
              and a.creatorUserId = :userId
            order by a.endAt desc
            """)
    java.util.List<Activity> findCreatedEndedActivities(
            @Param("userId") Long userId,
            @Param("status") String status,
            @Param("now") LocalDateTime now
    );
}
