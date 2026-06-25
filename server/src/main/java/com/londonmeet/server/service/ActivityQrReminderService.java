package com.londonmeet.server.service;

import com.londonmeet.pojo.entity.Activity;
import com.londonmeet.pojo.entity.Notification;
import com.londonmeet.server.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ActivityQrReminderService {

    private static final String STATUS_PUBLISHED = "PUBLISHED";

    private final ActivityRepository activityRepository;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void remindExpiringQrCodes() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextDay = now.plusHours(24);
        for (Activity activity : activityRepository.findByStatusAndEndAtAfterAndQrExpiresAtBetween(
                STATUS_PUBLISHED, now, now, nextDay)) {
            if (activity.getCreatorUserId() == null || activity.getQrReminderSentAt() != null) {
                continue;
            }
            notificationService.createNotification(
                    activity.getCreatorUserId(),
                    Notification.TYPE_ACTIVITY_QR_EXPIRING,
                    "群二维码即将失效",
                    "「" + activity.getTitle() + "」的群二维码将在1天内失效，请前往主页—我的活动更换。",
                    Notification.RELATED_ACTIVITY,
                    activity.getId()
            );
            activity.setQrReminderSentAt(now);
            activityRepository.save(activity);
        }
    }
}
