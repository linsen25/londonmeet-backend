package com.londonmeet.server.repository;

import com.londonmeet.pojo.entity.ActivityOrganizerBlacklist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ActivityOrganizerBlacklistRepository extends JpaRepository<ActivityOrganizerBlacklist, Long> {

    Optional<ActivityOrganizerBlacklist> findByOrganizerUserIdAndBlockedUserIdAndActiveTrue(
            Long organizerUserId,
            Long blockedUserId
    );

    Optional<ActivityOrganizerBlacklist> findByOrganizerUserIdAndBlockedUserId(
            Long organizerUserId,
            Long blockedUserId
    );

    List<ActivityOrganizerBlacklist> findByOrganizerUserIdAndActiveTrueOrderByCreatedAtDesc(Long organizerUserId);

    boolean existsByOrganizerUserIdAndBlockedUserIdAndActiveTrue(Long organizerUserId, Long blockedUserId);
}
