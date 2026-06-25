package com.londonmeet.server.service;

import com.londonmeet.pojo.entity.Activity;
import com.londonmeet.pojo.entity.ActivityRegistration;
import com.londonmeet.pojo.entity.Notification;
import com.londonmeet.server.repository.ActivityRegistrationRepository;
import com.londonmeet.server.repository.ActivityRepository;
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
public class ActivityLifecycleNotificationService {

    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final List<String> PARTICIPANT_STATUSES = List.of(
            ActivityRegistration.STATUS_APPROVED,
            ActivityRegistration.STATUS_JOINED_GROUP
    );

    private final ActivityRepository activityRepository;
    private final ActivityRegistrationRepository activityRegistrationRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 5 * * * *")
    @Transactional
    public void sendLifecycleNotifications() {
        LocalDateTime now = LocalDateTime.now();
        sendStartReminders(now);
        sendReviewAvailable(now);
    }

    private void sendStartReminders(LocalDateTime now) {
        for (Activity activity : activityRepository.findByStatusAndStartAtBetween(
                STATUS_PUBLISHED, now, now.plusHours(24))) {
            notifyRelatedOnce(
                    activity,
                    Notification.TYPE_ACTIVITY_START_REMINDER,
                    "活动即将开始",
                    "「" + activity.getTitle() + "」将在24小时内开始，请确认时间、地点和群聊信息。"
            );
        }
    }

    private void sendReviewAvailable(LocalDateTime now) {
        for (Activity activity : activityRepository.findByStatusAndEndAtBetween(
                STATUS_PUBLISHED, now.minusDays(7), now)) {
            notifyRelatedOnce(
                    activity,
                    Notification.TYPE_REVIEW_AVAILABLE,
                    "活动评价已开放",
                    "「" + activity.getTitle() + "」已经结束，评价将在结束7天后关闭，请前往主页—待评价完成评价。"
            );
        }
    }

    private void notifyRelatedOnce(Activity activity, String type, String title, String content) {
        for (Long userId : relatedUserIds(activity)) {
            if (notificationRepository.existsByUserIdAndTypeAndRelatedTypeAndRelatedId(
                    userId, type, Notification.RELATED_ACTIVITY, activity.getId())) {
                continue;
            }
            notificationService.createNotification(
                    userId, type, title, content, Notification.RELATED_ACTIVITY, activity.getId());
        }
    }

    private Set<Long> relatedUserIds(Activity activity) {
        Set<Long> ids = new LinkedHashSet<>();
        if (activity.getCreatorUserId() != null) {
            ids.add(activity.getCreatorUserId());
        }
        activityRegistrationRepository.findByActivityIdAndStatusIn(activity.getId(), PARTICIPANT_STATUSES)
                .stream()
                .map(ActivityRegistration::getUserId)
                .forEach(ids::add);
        return ids;
    }
}
