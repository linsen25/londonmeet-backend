package com.londonmeet.server.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.londonmeet.common.exception.BusinessException;
import com.londonmeet.pojo.dto.request.ReviewScoreRequest;
import com.londonmeet.pojo.dto.request.ReviewSubmitRequest;
import com.londonmeet.pojo.dto.request.ReviewBatchGoodRequest;
import com.londonmeet.pojo.entity.Activity;
import com.londonmeet.pojo.entity.ActivityRegistration;
import com.londonmeet.pojo.entity.ActivityReview;
import com.londonmeet.pojo.entity.User;
import com.londonmeet.pojo.vo.ReviewSubmitVO;
import com.londonmeet.pojo.vo.ReviewTaskVO;
import com.londonmeet.pojo.vo.ReviewBatchGoodVO;
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
import java.util.LinkedHashSet;
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
            "friendly"
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

        if (activityReviewRepository.existsByReviewerUserIdAndActivityIdAndTargetTypeAndTargetId(
                reviewerUserId, activity.getId(), mode, targetId)) {
            throw new BusinessException("该评价已经提交，不能重复评价。");
        }

        ActivityReview review = new ActivityReview();
        review.setReviewerUserId(reviewerUserId);
        review.setActivityId(activity.getId());
        review.setTargetType(mode);
        review.setTargetId(targetId);
        review.setOverallScore(overall);
        review.setScoresJson(toJson(scores));
        boolean lowScore = scores.stream().anyMatch(score -> score.getValue() < 3);
        String reason = trimToEmpty(request.getReason());
        if (lowScore && !StringUtils.hasText(reason)) {
            throw new BusinessException("存在低于3分的评分，请填写低分原因。");
        }
        if (reason.length() > 300) {
            throw new BusinessException("低分原因最多300字。");
        }
        review.setReason(StringUtils.hasText(reason) ? reason : null);
        review.setBatchGood(false);
        review.setStatus(lowScore ? ActivityReview.STATUS_PENDING : ActivityReview.STATUS_NORMAL);

        ActivityReview saved = activityReviewRepository.save(review);

        if (lowScore) {
            notificationService.createNotification(
                    reviewerUserId,
                    Notification.TYPE_REVIEW_MODERATED,
                    "低分评价已提交审核",
                    "你对「" + activity.getTitle() + "」提交的低分评价已进入管理员审核，审核前不会计入综合评分。",
                    Notification.RELATED_ACTIVITY,
                    activity.getId()
            );
        } else {
            Long receiverUserId = ActivityReview.TARGET_ACTIVITY.equals(mode)
                    ? activity.getCreatorUserId()
                    : targetId;
            User reviewer = userRepository.findById(reviewerUserId).orElse(null);
            if (receiverUserId != null && !receiverUserId.equals(reviewerUserId)) {
                notificationService.createNotification(
                        receiverUserId,
                        Notification.TYPE_REVIEW_RECEIVED,
                        ActivityReview.TARGET_ACTIVITY.equals(mode) ? "活动收到新评价" : "你收到一条成员评价",
                        resolveUserName(reviewer) + " 已完成「" + activity.getTitle() + "」的评价。",
                        Notification.RELATED_ACTIVITY,
                        activity.getId()
                );
            }
        }

        return ReviewSubmitVO.builder()
                .id(saved.getId())
                .mode(saved.getTargetType())
                .activityId(saved.getActivityId())
                .targetId(saved.getTargetId())
                .overallScore(saved.getOverallScore().doubleValue())
                .build();
    }

    @Override
    @Transactional
    public ReviewBatchGoodVO submitBatchGood(
            ReviewBatchGoodRequest request,
            LoginUser loginUser
    ) {
        Long reviewerUserId = requireUserId(loginUser);
        if (request == null || request.getActivityId() == null) {
            throw new BusinessException("Activity id is required.");
        }
        Activity activity = activityRepository.findById(request.getActivityId())
                .orElseThrow(() -> new BusinessException("Activity not found."));
        if (!reviewerUserId.equals(activity.getCreatorUserId())) {
            throw new BusinessException("只有活动发起人可以一键好评参与者。");
        }
        if (activity.getEndAt() == null || activity.getEndAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException("Activity has not ended yet.");
        }
        if (!activity.getEndAt().plusDays(REVIEW_WINDOW_DAYS).isAfter(LocalDateTime.now())) {
            throw new BusinessException("评价有效期为活动结束后7天，当前已过期。");
        }
        List<Long> targetIds = request.getTargetIds() == null
                ? List.of()
                : request.getTargetIds().stream()
                .filter(java.util.Objects::nonNull)
                .filter(id -> !id.equals(reviewerUserId))
                .distinct()
                .toList();
        if (targetIds.isEmpty()) {
            throw new BusinessException("请选择需要一键好评的参与者。");
        }
        List<Long> submittedTargetIds = new ArrayList<>();
        Map<Long, String> skippedTargets = new LinkedHashMap<>();
        List<ReviewScoreRequest> scores = MEMBER_KEYS.stream().map(key -> {
            ReviewScoreRequest score = new ReviewScoreRequest();
            score.setKey(key);
            score.setLabel(switch (key) {
                case "punctual" -> "准时守约";
                case "communication" -> "沟通配合";
                default -> "友善礼貌";
            });
            score.setValue(5.0);
            return score;
        }).toList();

        for (Long targetId : targetIds) {
            if (!canReviewTarget(reviewerUserId, activity, ActivityReview.TARGET_MEMBER, targetId)) {
                skippedTargets.put(targetId, "不是该活动的有效参与者");
                continue;
            }
            if (activityReviewRepository.existsByReviewerUserIdAndActivityIdAndTargetTypeAndTargetId(
                    reviewerUserId, activity.getId(), ActivityReview.TARGET_MEMBER, targetId)) {
                skippedTargets.put(targetId, "已经评价");
                continue;
            }
            ActivityReview review = ActivityReview.builder()
                    .reviewerUserId(reviewerUserId)
                    .activityId(activity.getId())
                    .targetType(ActivityReview.TARGET_MEMBER)
                    .targetId(targetId)
                    .overallScore(BigDecimal.valueOf(5).setScale(2))
                    .scoresJson(toJson(scores))
                    .batchGood(true)
                    .status(ActivityReview.STATUS_NORMAL)
                    .build();
            ActivityReview saved = activityReviewRepository.save(review);
            notificationService.createNotification(
                    targetId,
                    Notification.TYPE_REVIEW_RECEIVED,
                    "你收到一条成员评价",
                    resolveUserName(userRepository.findById(reviewerUserId).orElse(null))
                            + " 已完成「" + activity.getTitle() + "」的评价。",
                    Notification.RELATED_ACTIVITY,
                    activity.getId()
            );
            submittedTargetIds.add(saved.getTargetId());
        }
        return ReviewBatchGoodVO.builder()
                .submittedTargetIds(submittedTargetIds)
                .skippedTargets(skippedTargets)
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
        List<Activity> createdActivities = activities.stream()
                .filter(activity -> userId.equals(activity.getCreatorUserId()))
                .toList();
        if (createdActivities.isEmpty()) {
            return List.of();
        }
        List<Long> activityIds = createdActivities.stream().map(Activity::getId).toList();
        Map<Long, Activity> activityById = createdActivities.stream()
                .collect(Collectors.toMap(Activity::getId, Function.identity()));

        Map<Long, Map<Long, Long>> participantIdsByActivity = new LinkedHashMap<>();
        createdActivities.forEach(activity ->
                participantIdsByActivity.put(activity.getId(), new LinkedHashMap<>()));

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
                                .canBatchGood(userId.equals(activity.getCreatorUserId()))
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
        return reviewerUserId.equals(activity.getCreatorUserId())
                && targetId != null
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
