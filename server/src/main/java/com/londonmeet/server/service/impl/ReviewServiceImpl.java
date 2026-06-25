package com.londonmeet.server.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.londonmeet.common.exception.BusinessException;
import com.londonmeet.pojo.dto.request.ReviewScoreRequest;
import com.londonmeet.pojo.dto.request.ReviewSubmitRequest;
import com.londonmeet.pojo.entity.Activity;
import com.londonmeet.pojo.entity.ActivityRegistration;
import com.londonmeet.pojo.entity.ActivityReview;
import com.londonmeet.pojo.entity.User;
import com.londonmeet.pojo.vo.ReviewSubmitVO;
import com.londonmeet.pojo.vo.ReviewTaskVO;
import com.londonmeet.server.repository.ActivityRegistrationRepository;
import com.londonmeet.server.repository.ActivityRepository;
import com.londonmeet.server.repository.ActivityReviewRepository;
import com.londonmeet.server.repository.UserRepository;
import com.londonmeet.server.security.LoginUser;
import com.londonmeet.server.service.ReviewService;
import com.londonmeet.server.service.NotificationService;
import com.londonmeet.pojo.entity.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final int REVIEW_WINDOW_DAYS = 7;
    private static final List<String> PARTICIPANT_STATUSES = List.of(
            ActivityRegistration.STATUS_APPROVED,
            ActivityRegistration.STATUS_JOINED_GROUP
    );
    private static final List<String> ACTIVITY_KEYS = List.of(
            "organization",
            "experience",
            "atmosphere",
            "match"
    );
    private static final List<String> MEMBER_KEYS = List.of(
            "punctual",
            "communication",
            "friendly",
            "participation"
    );

    private final ActivityRepository activityRepository;
    private final ActivityRegistrationRepository activityRegistrationRepository;
    private final ActivityReviewRepository activityReviewRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    @Override
    @Transactional(readOnly = true)
    public List<ReviewTaskVO> listTasks(String mode, LoginUser loginUser) {
        Long userId = requireUserId(loginUser);
        String normalizedMode = normalizeMode(mode);

        List<Activity> activities = activityRepository.findRelatedEndedActivities(
                userId,
                STATUS_PUBLISHED,
                LocalDateTime.now(),
                PARTICIPANT_STATUSES
        ).stream()
                .filter(activity -> activity.getEndAt() != null)
                .filter(activity -> activity.getEndAt().plusDays(REVIEW_WINDOW_DAYS)
                        .isAfter(LocalDateTime.now()))
                .toList();
        if (activities.isEmpty()) {
            return List.of();
        }

        List<ReviewTaskVO> tasks = new ArrayList<>();
        if (normalizedMode == null || ActivityReview.TARGET_ACTIVITY.equals(normalizedMode)) {
            tasks.addAll(buildActivityTasks(userId, activities));
        }
        if (normalizedMode == null || ActivityReview.TARGET_MEMBER.equals(normalizedMode)) {
            tasks.addAll(buildMemberTasks(userId, activities));
        }

        return tasks.stream().limit(100).toList();
    }

    @Override
    @Transactional
    public ReviewSubmitVO submit(ReviewSubmitRequest request, LoginUser loginUser) {
        Long reviewerUserId = requireUserId(loginUser);
        if (request == null) {
            throw new BusinessException("Review request is required.");
        }

        String mode = normalizeMode(request.getMode());
        if (mode == null) {
            throw new BusinessException("Review mode is required.");
        }
        if (request.getActivityId() == null) {
            throw new BusinessException("Activity id is required.");
        }

        Activity activity = activityRepository.findById(request.getActivityId())
                .orElseThrow(() -> new BusinessException("Activity not found."));
        if (!STATUS_PUBLISHED.equals(activity.getStatus())) {
            throw new BusinessException("Activity is not available.");
        }
        if (activity.getEndAt() == null || activity.getEndAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException("Activity has not ended yet.");
        }
        if (!activity.getEndAt().plusDays(REVIEW_WINDOW_DAYS).isAfter(LocalDateTime.now())) {
            throw new BusinessException("评价有效期为活动结束后7天，当前已过期。");
        }

        Long targetId = ActivityReview.TARGET_ACTIVITY.equals(mode)
                ? ActivityReview.ACTIVITY_TARGET_ID
                : request.getTargetId();

        if (!canReviewTarget(reviewerUserId, activity, mode, targetId)) {
            throw new BusinessException("You cannot review this target.");
        }

        List<ReviewScoreRequest> scores = normalizeScores(mode, request.getScores());
        BigDecimal overall = average(scores);

        ActivityReview review = activityReviewRepository
                .findByReviewerUserIdAndActivityIdAndTargetTypeAndTargetId(
                        reviewerUserId,
                        activity.getId(),
                        mode,
                        targetId
                )
                .orElseGet(ActivityReview::new);

        review.setReviewerUserId(reviewerUserId);
        review.setActivityId(activity.getId());
        review.setTargetType(mode);
        review.setTargetId(targetId);
        review.setOverallScore(overall);
        review.setScoresJson(toJson(scores));
        if (review.getStatus() == null) {
            review.setStatus(ActivityReview.STATUS_NORMAL);
        }

        ActivityReview saved = activityReviewRepository.save(review);

        Long receiverUserId = ActivityReview.TARGET_ACTIVITY.equals(mode)
                ? activity.getCreatorUserId()
                : targetId;
        if (receiverUserId != null && !receiverUserId.equals(reviewerUserId)) {
            User reviewer = userRepository.findById(reviewerUserId).orElse(null);
            notificationService.createNotification(
                    receiverUserId,
                    Notification.TYPE_REVIEW_RECEIVED,
                    ActivityReview.TARGET_ACTIVITY.equals(mode) ? "活动收到新评价" : "你收到一条成员评价",
                    resolveUserName(reviewer) + " 已完成「" + activity.getTitle() + "」的评价。",
                    Notification.RELATED_ACTIVITY,
                    activity.getId()
            );
        }

        return ReviewSubmitVO.builder()
                .id(saved.getId())
                .mode(saved.getTargetType())
                .activityId(saved.getActivityId())
                .targetId(saved.getTargetId())
                .overallScore(saved.getOverallScore().doubleValue())
                .build();
    }

    private List<ReviewTaskVO> buildActivityTasks(Long userId, List<Activity> activities) {
        return activities.stream()
                .filter(activity -> !userId.equals(activity.getCreatorUserId()))
                .filter(activity -> !activityReviewRepository.existsByReviewerUserIdAndActivityIdAndTargetTypeAndTargetId(
                        userId,
                        activity.getId(),
                        ActivityReview.TARGET_ACTIVITY,
                        ActivityReview.ACTIVITY_TARGET_ID
                ))
                .map(activity -> ReviewTaskVO.builder()
                        .id("activity-" + activity.getId())
                        .mode(ActivityReview.TARGET_ACTIVITY)
                        .activityId(activity.getId())
                        .targetId(ActivityReview.ACTIVITY_TARGET_ID)
                        .title(activity.getTitle())
                        .activityTitle(activity.getTitle())
                        .avatarUrl(activity.getCoverUrl())
                        .startAt(toEpochMillis(activity.getStartAt()))
                        .endAt(toEpochMillis(activity.getEndAt()))
                        .overallRating(0.0)
                        .timelinessRating(0.0)
                        .reviewCount(0)
                        .build())
                .toList();
    }

    private List<ReviewTaskVO> buildMemberTasks(Long userId, List<Activity> activities) {
        List<Long> activityIds = activities.stream().map(Activity::getId).toList();
        Map<Long, Activity> activityById = activities.stream()
                .collect(Collectors.toMap(Activity::getId, Function.identity()));

        Map<Long, Map<Long, Long>> participantIdsByActivity = new LinkedHashMap<>();
        activities.forEach(activity -> {
            Map<Long, Long> ids = new LinkedHashMap<>();
            if (activity.getCreatorUserId() != null) {
                ids.put(activity.getCreatorUserId(), activity.getCreatorUserId());
            }
            participantIdsByActivity.put(activity.getId(), ids);
        });

        List<ActivityRegistration> registrations =
                activityRegistrationRepository.findByActivityIdInAndStatusIn(activityIds, PARTICIPANT_STATUSES);
        registrations.forEach(registration -> participantIdsByActivity
                .computeIfAbsent(registration.getActivityId(), ignored -> new LinkedHashMap<>())
                .put(registration.getUserId(), registration.getUserId()));

        List<Long> userIds = participantIdsByActivity.values().stream()
                .flatMap(ids -> ids.keySet().stream())
                .distinct()
                .toList();
        Map<Long, User> userById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<ReviewTaskVO> tasks = new ArrayList<>();
        participantIdsByActivity.forEach((activityId, participantIds) -> {
            Activity activity = activityById.get(activityId);
            if (activity == null) {
                return;
            }

            participantIds.keySet().stream()
                    .filter(targetId -> !targetId.equals(userId))
                    .filter(targetId -> !activityReviewRepository.existsByReviewerUserIdAndActivityIdAndTargetTypeAndTargetId(
                            userId,
                            activityId,
                            ActivityReview.TARGET_MEMBER,
                            targetId
                    ))
                    .forEach(targetId -> {
                        User user = userById.get(targetId);
                        tasks.add(ReviewTaskVO.builder()
                                .id("member-" + activityId + "-" + targetId)
                                .mode(ActivityReview.TARGET_MEMBER)
                                .activityId(activityId)
                                .targetId(targetId)
                                .title(resolveUserName(user))
                                .activityTitle(activity.getTitle())
                                .name(resolveUserName(user))
                                .avatarUrl(user == null ? null : user.getAvatarUrl())
                                .startAt(toEpochMillis(activity.getStartAt()))
                                .endAt(toEpochMillis(activity.getEndAt()))
                                .overallRating(0.0)
                                .timelinessRating(0.0)
                                .reviewCount(activityReviewRepository.countByTargetTypeAndTargetIdAndStatus(
                                        ActivityReview.TARGET_MEMBER,
                                        targetId,
                                        ActivityReview.STATUS_NORMAL
                                ))
                                .build());
                    });
        });

        return tasks;
    }

    private boolean canReviewTarget(Long reviewerUserId, Activity activity, String mode, Long targetId) {
        if (!isActivityParticipant(reviewerUserId, activity.getId(), activity.getCreatorUserId())) {
            return false;
        }
        if (ActivityReview.TARGET_ACTIVITY.equals(mode)) {
            return !reviewerUserId.equals(activity.getCreatorUserId())
                    && targetId != null
                    && targetId == ActivityReview.ACTIVITY_TARGET_ID;
        }
        return targetId != null
                && !targetId.equals(reviewerUserId)
                && isActivityParticipant(targetId, activity.getId(), activity.getCreatorUserId());
    }

    private boolean isActivityParticipant(Long userId, Long activityId, Long creatorUserId) {
        if (userId == null) {
            return false;
        }
        if (userId.equals(creatorUserId)) {
            return true;
        }
        return activityRegistrationRepository.findByActivityIdAndUserId(activityId, userId)
                .map(registration -> PARTICIPANT_STATUSES.contains(registration.getStatus()))
                .orElse(false);
    }

    private List<ReviewScoreRequest> normalizeScores(String mode, List<ReviewScoreRequest> scores) {
        List<String> requiredKeys = ActivityReview.TARGET_MEMBER.equals(mode) ? MEMBER_KEYS : ACTIVITY_KEYS;
        if (scores == null || scores.size() != requiredKeys.size()) {
            throw new BusinessException("Please complete all rating dimensions.");
        }

        Map<String, ReviewScoreRequest> scoreByKey = scores.stream()
                .filter(score -> score != null && StringUtils.hasText(score.getKey()))
                .collect(Collectors.toMap(ReviewScoreRequest::getKey, Function.identity(), (first, ignored) -> first));

        List<ReviewScoreRequest> normalized = new ArrayList<>();
        for (String key : requiredKeys) {
            ReviewScoreRequest score = scoreByKey.get(key);
            if (score == null || score.getValue() == null || score.getValue() < 1 || score.getValue() > 5) {
                throw new BusinessException("Scores must be between 1 and 5.");
            }
            ReviewScoreRequest clean = new ReviewScoreRequest();
            clean.setKey(key);
            clean.setLabel(trimToEmpty(score.getLabel()));
            clean.setValue(score.getValue());
            normalized.add(clean);
        }
        return normalized;
    }

    private BigDecimal average(List<ReviewScoreRequest> scores) {
        double total = scores.stream().mapToDouble(ReviewScoreRequest::getValue).sum();
        return BigDecimal.valueOf(total / scores.size()).setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeMode(String mode) {
        if (!StringUtils.hasText(mode)) {
            return null;
        }
        String value = mode.trim().toLowerCase();
        if (ActivityReview.TARGET_ACTIVITY.equals(value) || ActivityReview.TARGET_MEMBER.equals(value)) {
            return value;
        }
        return null;
    }

    private Long requireUserId(LoginUser loginUser) {
        if (loginUser == null || loginUser.userId() == null) {
            throw new BusinessException("Please login first.");
        }
        return loginUser.userId();
    }

    private Long toEpochMillis(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private String resolveUserName(User user) {
        if (user == null) {
            return "MeetFun User";
        }
        if (StringUtils.hasText(user.getNickname())) {
            return user.getNickname().trim();
        }
        String openid = user.getOpenid();
        if (StringUtils.hasText(openid) && openid.length() > 6) {
            return "User " + openid.substring(openid.length() - 6);
        }
        return "MeetFun User";
    }

    private String trimToEmpty(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("Failed to serialize review data.");
        }
    }
}
