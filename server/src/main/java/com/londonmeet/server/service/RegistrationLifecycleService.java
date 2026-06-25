package com.londonmeet.server.service;

import com.londonmeet.pojo.entity.Activity;
import com.londonmeet.pojo.entity.ActivityRegistration;
import com.londonmeet.pojo.entity.Notification;
import com.londonmeet.server.repository.ActivityRegistrationRepository;
import com.londonmeet.server.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RegistrationLifecycleService {

    private final ActivityRegistrationRepository activityRegistrationRepository;
    private final ActivityRepository activityRepository;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void closePendingRegistrationsForStartedActivities() {
        LocalDateTime now = LocalDateTime.now();
        for (ActivityRegistration registration :
                activityRegistrationRepository.findStartedPendingRegistrations(
                        ActivityRegistration.STATUS_PENDING, now)) {
            Activity activity = activityRepository.findById(registration.getActivityId()).orElse(null);
            if (activity == null) {
                continue;
            }
            registration.setStatus(ActivityRegistration.STATUS_EXPIRED);
            registration.setNoticeCode(ActivityRegistration.NOTICE_EXPIRED);
            registration.setReviewedAt(now);
            activityRegistrationRepository.save(registration);
            notificationService.createNotification(
                    registration.getUserId(),
                    Notification.TYPE_REGISTRATION_EXPIRED,
                    "报名审核已结束",
                    "「" + activity.getTitle() + "」已经开始，你的待审核报名已自动关闭。",
                    Notification.RELATED_ACTIVITY,
                    activity.getId()
            );
        }
    }
}
