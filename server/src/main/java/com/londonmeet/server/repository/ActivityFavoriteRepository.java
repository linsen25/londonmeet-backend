package com.londonmeet.server.repository;

import com.londonmeet.pojo.entity.ActivityFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public interface ActivityFavoriteRepository extends JpaRepository<ActivityFavorite, Long> {

    Optional<ActivityFavorite> findByUserIdAndActivityId(Long userId, Long activityId);

    @Query("select f.activityId from ActivityFavorite f where f.userId = :userId and f.activityId in :activityIds")
    Set<Long> findActivityIdsByUserIdAndActivityIdIn(
            @Param("userId") Long userId,
            @Param("activityIds") Collection<Long> activityIds
    );
}
