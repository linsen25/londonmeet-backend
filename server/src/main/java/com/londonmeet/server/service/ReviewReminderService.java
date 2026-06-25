package com.londonmeet.server.service;

import com.londonmeet.pojo.entity.Activity;
import com.londonmeet.pojo.entity.ActivityRegistration;
import com.londonmeet.pojo.entity.ActivityReview;
import com.londonmeet.pojo.entity.Notification;
import com.londonmeet.server.repository.ActivityRegistrationRepository;
import com.londonmeet.server.repository.ActivityRepository;
import com.londonmeet.server.repository.ActivityReviewRepository;
import com.londonmeet.server.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ReviewReminderService {

    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final List<String> PARTICIPANT_STATUSES = List.of(
            ActivityRegistration.STATUS_APPROVED,
            ActivityRegistration.STATUS_JOINED_GROUP
    );

    private final ActivityRepository activityRepository;
    private final ActivityRegistrationRepository activityRegistrationRepository;
    private final ActivityReviewRepository activityReviewRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 15 * * * *")
    @Transactional
    public void remindPendingReviews() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endedFrom = now.minusDays(7);
        LocalDateTime endedTo = now.minusDays(5);

        for (Activity activity : activityRepository.findByStatusAndEndAtBetween(
                STATUS_PUBLISHED, endedFrom, endedTo)) {
            Set<Long> participantIds = new LinkedHashSet<>();
            if (activity.getCreatorUserId() != null) {
                participantIds.add(activity.getCreatorUserId());
            }
            activityRegistrationRepository
                    .findByActivityIdAndStatusIn(activity.getId(), PARTICIPANT_STATUSES)
                    .stream()
                    .map(ActivityRegistration::getUserId)
                    .forEach(participantIds::add);

            for (Long userId : participantIds) {
                if (!hasPendingReview(
                        userId, activity.getId(), activity.getCreatorUserId(), participantIds)) {
                    continue;
                }
                if (notificationRepository.existsByUserIdAndTypeAndRelatedTypeAndRelatedId(
                        userId,
                        Notification.TYPE_REVIEW_REMINDER,
                        Notification.RELATED_ACTIVITY,
                        activity.getId())) {
                    continue;
                }
                notificationService.createNotification(
                        userId,
                        Notification.TYPE_REVIEW_REMINDER,
                        "活动评价即将到期",
                        "「" + activity.getTitle() + "」的评价将在活动结束7天后关闭，请尽快完成评价。",
                        Notification.RELATED_ACTIVITY,
                        activity.getId()
                );
            }
        }
    }

    private boolean hasPendingReview(
            Long userId,
            Long activityId,
            Long creatorUserId,
            Set<Long> participantIds
    ) {
        boolean activityPending =
                !userId.equals(creatorUserId)
                && !activityReviewRepository.existsByReviewerUserIdAndActivityIdAndTargetTypeAndTargetId(
                        userId,
                        activityId,
                        ActivityReview.TARGET_ACTIVITY,
                        ActivityReview.ACTIVITY_TARGET_ID
                );
        if (activityPending) {
            return true;
        }
        return participantIds.stream()
                .filter(targetId -> !targetId.equals(userId))
                .anyMatch(targetId ->
                        !activityReviewRepository.existsByReviewerUserIdAndActivityIdAndTargetTypeAndTargetId(
                                userId,
                                activityId,
                                ActivityReview.TARGET_MEMBER,
                                targetId
                        ));
    }
}
