package com.londonmeet.server.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.londonmeet.common.exception.BusinessException;
import com.londonmeet.pojo.dto.request.ActivityApplyRequest;
import com.londonmeet.pojo.dto.request.ActivityCreateRequest;
import com.londonmeet.pojo.dto.request.ActivityFavoriteRequest;
import com.londonmeet.pojo.dto.request.ActivityQueryRequest;
import com.londonmeet.pojo.dto.request.ActivitySearchRequest;
import com.londonmeet.pojo.dto.request.ActivityReportRequest;
import com.londonmeet.pojo.dto.request.ActivityUpdateRequest;
import com.londonmeet.pojo.dto.request.ActivityQrUpdateRequest;
import com.londonmeet.pojo.dto.request.ActivityCancelRegistrationRequest;
import com.londonmeet.pojo.dto.request.ActivityEventRequest;
import com.londonmeet.pojo.entity.Activity;
import com.londonmeet.pojo.entity.ActivityRegistration;
import com.londonmeet.pojo.entity.ActivityFavorite;
import com.londonmeet.pojo.entity.ActivityReview;
import com.londonmeet.pojo.entity.ActivityReport;
import com.londonmeet.pojo.entity.Notification;
import com.londonmeet.pojo.entity.Tag;
import com.londonmeet.pojo.entity.User;
import com.londonmeet.pojo.vo.ActivityDetailVO;
import com.londonmeet.pojo.vo.ActivityFavoriteVO;
import com.londonmeet.pojo.vo.ActivityPageVO;
import com.londonmeet.pojo.vo.ActivityPostVO;
import com.londonmeet.pojo.vo.ActivityRegistrationVO;
import com.londonmeet.pojo.vo.ActivityReportVO;
import com.londonmeet.pojo.vo.PendingReviewVO;
import com.londonmeet.server.repository.ActivityRepository;
import com.londonmeet.server.repository.ActivityRegistrationRepository;
import com.londonmeet.server.repository.ActivityFavoriteRepository;
import com.londonmeet.server.repository.ActivityReviewRepository;
import com.londonmeet.server.repository.ActivityReportRepository;
import com.londonmeet.server.repository.TagRepository;
import com.londonmeet.server.repository.UserRepository;
import com.londonmeet.server.repository.NotificationRepository;
import com.londonmeet.server.security.LoginUser;
import com.londonmeet.server.service.ActivityService;
import com.londonmeet.server.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ActivityServiceImpl implements ActivityService {
    private static final String DEFAULT_PROFILE_MOTTO = "你好呀，准备好出去转转了么~";
    private static final String DEFAULT_PROFILE_TAG = "未添加标签";

    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final List<String> CAPACITY_STATUSES = List.of(
            ActivityRegistration.STATUS_APPROVED,
            ActivityRegistration.STATUS_JOINED_GROUP
    );
    private static final List<String> CONFLICT_STATUSES = List.of(
            ActivityRegistration.STATUS_PENDING,
            ActivityRegistration.STATUS_APPROVED,
            ActivityRegistration.STATUS_JOINED_GROUP
    );
    private static final int CREATE_MIN_LEAD_HOURS = 3;
    private static final int REGISTERED_TIME_MIN_LEAD_HOURS = 6;
    private static final int QR_CHANGE_REMINDER_COOLDOWN_HOURS = 6;
    private static final Map<String, String> CANCELLATION_REASON_LABELS = Map.of(
            "time_conflict", "时间冲突",
            "temporary_issue", "临时有事",
            "activity_changed", "活动信息变更",
            "location_inconvenient", "地点不便",
            "other", "其他"
    );
    private final ActivityRepository activityRepository;
    private final ActivityFavoriteRepository activityFavoriteRepository;
    private final ActivityRegistrationRepository activityRegistrationRepository;
    private final ActivityReviewRepository activityReviewRepository;
    private final ActivityReportRepository activityReportRepository;
    private final TagRepository tagRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final NotificationRepository notificationRepository;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    private static final Set<String> ANALYTICS_EVENT_TYPES =
            Set.of("EXPOSURE", "DETAIL_VIEW", "QR_OPEN");

    @Override
    @Transactional
    public ActivityPostVO createActivity(ActivityCreateRequest request, LoginUser loginUser) {
        if (loginUser == null || loginUser.userId() == null) {
            throw new BusinessException("Please login before creating an activity.");
        }

        User creator = userRepository.findById(loginUser.userId())
                .orElseThrow(() -> new BusinessException("Login user not found."));

        LocalDateTime startAt = toLocalDateTime(request.getStartAt());
        LocalDateTime endAt = toLocalDateTime(request.getEndAt());
        LocalDateTime now = LocalDateTime.now();
        if (startAt.isBefore(now.plusHours(CREATE_MIN_LEAD_HOURS))) {
            throw new BusinessException("活动开始时间必须至少晚于当前时间3小时。");
        }
        if (startAt.isAfter(now.plusDays(30))) {
            throw new BusinessException("活动开始时间最多可设置在30天内。");
        }
        if (!endAt.isAfter(startAt)) {
            throw new BusinessException("Activity end time must be after start time.");
        }
        if (activityRepository.existsCreatorTimeConflict(
                creator.getId(), STATUS_PUBLISHED, startAt, endAt, null)) {
            throw new BusinessException("该时间与你发起的其他活动冲突。");
        }

        List<String> imageUrls = sanitizeList(request.getImageUrls(), 4);
        String coverUrl = firstText(imageUrls);
        if (!StringUtils.hasText(coverUrl)) {
            throw new BusinessException("At least one activity image is required.");
        }

        List<Long> tagIds = sanitizeTagIds(resolveRequestTagIds(request), 4);
        if (tagIds.isEmpty()) {
            throw new BusinessException("At least one activity tag is required.");
        }
        List<String> tagNames = resolveTagNames(tagIds);
        Long tagId = tagIds.get(0);

        if (request.getRecruitCount() == null || request.getRecruitCount() < 1) {
            throw new BusinessException("Recruit count is required.");
        }

        if (!StringUtils.hasText(request.getInviteQrUrl())) {
            throw new BusinessException("Invite QR code is required.");
        }

        Activity activity = Activity.builder()
                .title(request.getTitle().trim())
                .content(request.getContent().trim())
                .creatorUserId(creator.getId())
                .authorName(resolveAuthorName(creator))
                .avatarUrl(creator.getAvatarUrl())
                .coverUrl(coverUrl)
                .startAt(startAt)
                .originalStartAt(startAt)
                .endAt(endAt)
                .tagId(tagId)
                .tagIdsJson(toJson(tagIds))
                .tagsJson(toJson(tagNames))
                .recruitCount(normalizeRecruitCount(request.getRecruitCount()))
                .locationText(request.getLocationText().trim())
                .mapImageUrl(trimToNull(request.getMapImageUrl()))
                .imageUrlsJson(toJson(imageUrls))
                .inviteQrUrl(trimToNull(request.getInviteQrUrl()))
                .editCount(0)
                .qrExpiresAt(LocalDateTime.now().plusDays(request.getInviteQrRemainingDays()))
                .status(STATUS_PUBLISHED)
                .build();

        Activity saved = activityRepository.save(activity);
        notificationService.createNotification(
                creator.getId(),
                Notification.TYPE_ACTIVITY_PUBLISHED,
                "活动已成功刊登",
                "你的活动「" + saved.getTitle()
                        + "」已经成功刊登。活动开始前可继续修改；如已有用户报名，调整开始时间必须至少晚于当前时间6小时。",
                Notification.RELATED_ACTIVITY,
                saved.getId()
        );
        return toPostVO(saved, false);
    }

    @Override
    @Transactional
    public void recordEvents(ActivityEventRequest request, LoginUser loginUser) {
        Long userId = requireUserId(loginUser);
        if (request == null || !StringUtils.hasText(request.getEventType())) {
            throw new BusinessException("Event type is required.");
        }
        String eventType = request.getEventType().trim().toUpperCase();
        if (!ANALYTICS_EVENT_TYPES.contains(eventType)) {
            throw new BusinessException("Unsupported analytics event.");
        }
        List<Long> activityIds = request.getActivityIds() == null
                ? List.of()
                : request.getActivityIds().stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .limit(50)
                .toList();
        if (activityIds.isEmpty()) {
            return;
        }
        Map<Long, Activity> activityMap = activityRepository.findAllById(activityIds).stream()
                .collect(Collectors.toMap(Activity::getId, Function.identity()));
        LocalDateTime hour = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);
        for (Long activityId : activityIds) {
            Activity activity = activityMap.get(activityId);
            if (activity == null) continue;
            if ("QR_OPEN".equals(eventType)) {
                if ("DISABLED".equals(loginUser.status())) continue;
                if (!canOpenActivityQr(activity, LocalDateTime.now())) continue;
                boolean approvedParticipant = activityRegistrationRepository
                        .findByActivityIdAndUserId(activityId, userId)
                        .map(registration -> CAPACITY_STATUSES.contains(registration.getStatus()))
                        .orElse(false);
                if (!approvedParticipant) continue;
            }
            jdbcTemplate.update("""
                    INSERT IGNORE INTO activity_analytics_events
                        (user_id, activity_id, event_type, event_hour, created_at)
                    VALUES (?, ?, ?, ?, NOW())
                    """, userId, activityId, eventType, hour);
        }
    }

    @Override
    @Transactional
    public ActivityPageVO listActivities(ActivityQueryRequest request, LoginUser loginUser) {
        Long userId = requireUserId(loginUser);
        String range = normalizeRange(request.getRange());
        int page = normalizePage(request.getPage());
        int pageSize = normalizePageSize(request.getPageSize());

        LocalDateTime now = LocalDateTime.now();

        LocalDate today = now.toLocalDate();
        LocalDateTime rangeEnd = switch (range) {
            case "week" -> today.plusWeeks(1).plusDays(1).atStartOfDay();
            case "month" -> today.plusMonths(1).plusDays(1).atStartOfDay();
            default -> today.plusDays(1).atStartOfDay();
        };

        Page<Activity> result = activityRepository.findByStatusAndEndAtAfterAndEndAtBefore(
                STATUS_PUBLISHED,
                now,
                rangeEnd,
                PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        List<ActivityPostVO> list = toPostVOs(result.getContent(), userId);

        return ActivityPageVO.builder()
                .list(list)
                .page(page)
                .pageSize(pageSize)
                .hasMore(result.hasNext())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ActivityPageVO searchActivities(ActivitySearchRequest request, LoginUser loginUser) {
        Long userId = requireUserId(loginUser);
        int page = normalizePage(request.getPage());
        int pageSize = normalizePageSize(request.getPageSize());
        String keyword = trimToNull(request.getKeyword());
        List<Long> tagIds = resolveSearchTagIds(request.getTags());

        if (keyword == null && tagIds.isEmpty()) {
            return ActivityPageVO.builder()
                    .list(List.of())
                    .page(page)
                    .pageSize(pageSize)
                    .hasMore(false)
                    .build();
        }

        Page<Activity> result = activityRepository.searchPublishedActivities(
                STATUS_PUBLISHED,
                LocalDateTime.now(),
                keyword,
                !tagIds.isEmpty(),
                tagIds.isEmpty() ? List.of(-1L) : tagIds,
                PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        return ActivityPageVO.builder()
                .list(toPostVOs(result.getContent(), userId))
                .page(page)
                .pageSize(pageSize)
                .hasMore(result.hasNext())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ActivityPageVO listMyOngoingActivities(ActivityQueryRequest request, LoginUser loginUser) {
        Long userId = requireUserId(loginUser);
        int page = normalizePage(request.getPage());
        int pageSize = normalizePageSize(request.getPageSize());

        Page<Activity> result = activityRepository.findRelatedOngoingActivities(
                userId,
                STATUS_PUBLISHED,
                LocalDateTime.now(),
                CONFLICT_STATUSES,
                PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.ASC, "endAt"))
        );

        return ActivityPageVO.builder()
                .list(toPostVOs(result.getContent(), userId))
                .page(page)
                .pageSize(pageSize)
                .hasMore(result.hasNext())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ActivityPageVO listMyCreatedActivities(ActivityQueryRequest request, LoginUser loginUser) {
        Long userId = requireUserId(loginUser);
        int page = normalizePage(request.getPage());
        int pageSize = normalizePageSize(request.getPageSize());
        Page<Activity> result = activityRepository.findByCreatorUserIdAndStatusAndEndAtAfter(
                userId,
                STATUS_PUBLISHED,
                LocalDateTime.now(),
                PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.ASC, "startAt"))
        );
        return ActivityPageVO.builder()
                .list(toPostVOs(result.getContent(), userId))
                .page(page)
                .pageSize(pageSize)
                .hasMore(result.hasNext())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ActivityPageVO listFavoriteActivities(ActivityQueryRequest request, LoginUser loginUser) {
        Long userId = requireUserId(loginUser);
        int page = normalizePage(request.getPage());
        int pageSize = normalizePageSize(request.getPageSize());

        Page<Activity> result = activityRepository.findActiveFavorites(
                userId,
                STATUS_PUBLISHED,
                LocalDateTime.now(),
                PageRequest.of(page - 1, pageSize)
        );

        return ActivityPageVO.builder()
                .list(result.getContent().stream().map(activity -> toPostVO(activity, true)).toList())
                .page(page)
                .pageSize(pageSize)
                .hasMore(result.hasNext())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ActivityPageVO listHistoryActivities(ActivityQueryRequest request, LoginUser loginUser) {
        Long userId = requireUserId(loginUser);
        int page = normalizePage(request.getPage());
        int pageSize = normalizePageSize(request.getPageSize());
        String type = request == null || request.getType() == null
                ? "joined"
                : request.getType().trim().toLowerCase();
        List<Activity> all = "created".equals(type)
                ? activityRepository.findCreatedEndedActivities(
                        userId, STATUS_PUBLISHED, LocalDateTime.now())
                : activityRepository.findJoinedEndedActivities(
                        userId, STATUS_PUBLISHED, LocalDateTime.now(), CAPACITY_STATUSES);
        int from = Math.min((page - 1) * pageSize, all.size());
        int to = Math.min(from + pageSize, all.size());

        return ActivityPageVO.builder()
                .list(toPostVOs(all.subList(from, to), userId))
                .page(page)
                .pageSize(pageSize)
                .hasMore(to < all.size())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingReviewVO> listPendingReviews(LoginUser loginUser) {
        Long creatorUserId = requireUserId(loginUser);
        List<ActivityRegistration> registrations =
                activityRegistrationRepository.findByCreatorUserIdAndStatusOrderByCreatedAtAsc(
                        creatorUserId,
                        ActivityRegistration.STATUS_PENDING
                );

        if (registrations.isEmpty()) {
            return List.of();
        }

        List<Long> activityIds = registrations.stream()
                .map(ActivityRegistration::getActivityId)
                .distinct()
                .toList();
        List<Long> userIds = registrations.stream()
                .map(ActivityRegistration::getUserId)
                .distinct()
                .toList();

        Map<Long, Activity> activityById = activityRepository.findAllById(activityIds).stream()
                .collect(Collectors.toMap(Activity::getId, Function.identity()));
        Map<Long, User> userById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return registrations.stream()
                .map(registration -> {
                    Activity activity = activityById.get(registration.getActivityId());
                    User user = userById.get(registration.getUserId());
                    Map<String, Double> memberRatings = resolveMemberRatings(registration.getUserId());

                    return PendingReviewVO.builder()
                            .registrationId(registration.getId())
                            .activityId(registration.getActivityId())
                            .userId(registration.getUserId())
                            .activityTitle(activity == null ? "" : activity.getTitle())
                            .nickname(user == null ? "MeetFun User" : resolveAuthorName(user))
                            .avatarUrl(user == null ? null : user.getAvatarUrl())
                            .applicationText(registration.getApplicationText())
                            .punctualRating(memberRatings.get("punctual"))
                            .communicationRating(memberRatings.get("communication"))
                            .friendlyRating(memberRatings.get("friendly"))
                            .reviewCount(activityReviewRepository
                                    .countByTargetTypeAndTargetIdAndStatusAndCreatedAtGreaterThanEqual(
                                            ActivityReview.TARGET_MEMBER,
                                            registration.getUserId(),
                                            ActivityReview.STATUS_NORMAL,
                                            LocalDateTime.now().minusDays(30)))
                            .appliedAt(toEpochMillis(registration.getCreatedAt()))
                            .build();
                })
                .toList();
    }

    private Map<String, Double> resolveMemberRatings(Long userId) {
        List<ActivityReview> reviews = activityReviewRepository.findByTargetTypeAndTargetIdAndStatus(
                ActivityReview.TARGET_MEMBER,
                userId,
                ActivityReview.STATUS_NORMAL
        ).stream()
                .filter(review -> review.getCreatedAt() != null
                        && !review.getCreatedAt().isBefore(LocalDateTime.now().minusDays(30)))
                .toList();
        if (reviews.isEmpty()) {
            return Map.of();
        }
        Map<String, List<Double>> values = new HashMap<>();
        for (ActivityReview review : reviews) {
            try {
                List<com.londonmeet.pojo.dto.request.ReviewScoreRequest> scores =
                        objectMapper.readValue(
                                review.getScoresJson(),
                                new TypeReference<List<com.londonmeet.pojo.dto.request.ReviewScoreRequest>>() {}
                        );
                for (com.londonmeet.pojo.dto.request.ReviewScoreRequest score : scores) {
                    if (score != null && StringUtils.hasText(score.getKey()) && score.getValue() != null) {
                        values.computeIfAbsent(score.getKey(), ignored -> new ArrayList<>())
                                .add(score.getValue());
                    }
                }
            } catch (Exception ignored) {
                // Skip malformed historical review data.
            }
        }
        Map<String, Double> result = new HashMap<>();
        for (String key : List.of("punctual", "communication", "friendly")) {
            List<Double> scores = values.getOrDefault(key, List.of());
            if (!scores.isEmpty()) {
                double average = scores.stream().mapToDouble(Double::doubleValue).average().orElse(0);
                result.put(key, Math.round(average * 10.0) / 10.0);
            }
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public ActivityDetailVO getActivityDetail(Long id, LoginUser loginUser) {
        Activity activity = activityRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Activity not found."));

        Optional<ActivityRegistration> registration = Optional.empty();
        boolean isCreator = false;
        if (loginUser != null && loginUser.userId() != null) {
            registration = activityRegistrationRepository.findByActivityIdAndUserId(id, loginUser.userId());
            isCreator = loginUser.userId().equals(activity.getCreatorUserId());
        }

        if (!STATUS_PUBLISHED.equals(activity.getStatus()) && !isCreator) {
            throw new BusinessException("Activity is not available.");
        }

        boolean favorited = loginUser != null
                && loginUser.userId() != null
                && activityFavoriteRepository.findByUserIdAndActivityId(loginUser.userId(), id).isPresent();

        boolean canExposeQr = loginUser == null || !"DISABLED".equals(loginUser.status());
        return toDetailVO(activity, registration.orElse(null), isCreator, favorited, canExposeQr);
    }

    @Override
    @Transactional
    public ActivityDetailVO updateActivity(
            Long id,
            ActivityUpdateRequest request,
            LoginUser loginUser
    ) {
        Long userId = requireUserId(loginUser);
        Activity activity = activityRepository.findLockedById(id)
                .orElseThrow(() -> new BusinessException("Activity not found."));
        requireCreator(activity, userId);
        if (!STATUS_PUBLISHED.equals(activity.getStatus())) {
            throw new BusinessException("该活动不可修改。");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime newStart = toLocalDateTime(request.getStartAt());
        LocalDateTime newEnd = toLocalDateTime(request.getEndAt());
        if (!newEnd.isAfter(newStart)) {
            throw new BusinessException("活动结束时间必须晚于开始时间。");
        }

        int approvedCount = capacityCount(activity.getId());
        if (request.getRecruitCount() < approvedCount) {
            throw new BusinessException("招募人数不能少于已通过人数 " + approvedCount + "。");
        }

        List<String> imageUrls = sanitizeList(request.getImageUrls(), 4);
        String coverUrl = firstText(imageUrls);
        if (!StringUtils.hasText(coverUrl)) {
            throw new BusinessException("至少需要一张活动图片。");
        }
        List<Long> tagIds = sanitizeTagIds(request.getTagIds(), 4);
        if (tagIds.isEmpty()) {
            throw new BusinessException("至少选择一个活动标签。");
        }

        boolean hasRegistrations = activityRegistrationRepository.countByActivityId(activity.getId()) > 0;
        boolean started = !now.isBefore(activity.getStartAt());
        boolean ended = now.isAfter(activity.getEndAt());
        boolean timeChanged = !Objects.equals(activity.getStartAt(), newStart)
                || !Objects.equals(activity.getEndAt(), newEnd);
        boolean locationChanged = textChanged(activity.getLocationText(), request.getLocationText());
        boolean mapImageChanged = textChanged(activity.getMapImageUrl(), request.getMapImageUrl());
        boolean imagesChanged = !parseStringList(activity.getImageUrlsJson()).equals(imageUrls);
        boolean contentChanged = textChanged(activity.getContent(), request.getContent());
        boolean tagsChanged = !parseLongList(activity.getTagIdsJson()).equals(tagIds);
        boolean recruitChanged = !Objects.equals(
                normalizeRecruitCount(activity.getRecruitCount()),
                normalizeRecruitCount(request.getRecruitCount()));
        boolean hasAnyChanges = timeChanged || locationChanged || mapImageChanged
                || imagesChanged || contentChanged || tagsChanged || recruitChanged;

        if (ended) {
            throw new BusinessException("活动已结束，不能修改活动信息。");
        }
        if (started) {
            if (timeChanged) {
                throw new BusinessException("活动已开始，不能修改活动时间。");
            }
            if (locationChanged || mapImageChanged) {
                throw new BusinessException("活动已开始，不能修改活动地点。");
            }
            if (imagesChanged) {
                throw new BusinessException("活动已开始，不能修改活动图片。");
            }
            if (tagsChanged || recruitChanged) {
                throw new BusinessException("活动已开始，不能修改活动标签或招募人数。");
            }
            if (contentChanged && !isAppendOnly(activity.getContent(), request.getContent())) {
                throw new BusinessException("活动已开始，正文只能补充说明，不能修改原内容。");
            }
        } else if (hasRegistrations && timeChanged
                && newStart.isBefore(now.plusHours(REGISTERED_TIME_MIN_LEAD_HOURS))) {
            throw new BusinessException("已有用户报名，修改后的活动开始时间必须至少晚于当前时间6小时。");
        } else if (!hasRegistrations && timeChanged
                && newStart.isBefore(now.plusHours(CREATE_MIN_LEAD_HOURS))) {
            throw new BusinessException("活动开始时间必须至少晚于当前时间3小时。");
        }

        if (timeChanged && activityRepository.existsCreatorTimeConflict(
                userId, STATUS_PUBLISHED, newStart, newEnd, activity.getId())) {
            throw new BusinessException("修改后的时间与你发起的其他活动冲突。");
        }

        if (!hasAnyChanges) {
            return toDetailVO(activity, null, true, false, true);
        }

        activity.setContent(request.getContent().trim());
        activity.setTagId(tagIds.get(0));
        activity.setTagIdsJson(toJson(tagIds));
        activity.setTagsJson(toJson(resolveTagNames(tagIds)));
        activity.setStartAt(newStart);
        activity.setEndAt(newEnd);
        activity.setRecruitCount(normalizeRecruitCount(request.getRecruitCount()));
        activity.setLocationText(request.getLocationText().trim());
        activity.setMapImageUrl(trimToNull(request.getMapImageUrl()));
        activity.setImageUrlsJson(toJson(imageUrls));
        activity.setCoverUrl(coverUrl);
        Activity saved = activityRepository.save(activity);

        if (hasRegistrations) {
            notifyRegisteredParticipants(
                    saved,
                    Notification.TYPE_ACTIVITY_UPDATED,
                    "活动信息已变更",
                    "您报名的「" + saved.getTitle() + "」信息已变更，请前往主页—活动中查看。"
            );
        }
        return toDetailVO(saved, null, true, false, true);
    }

    @Override
    @Transactional
    public ActivityDetailVO updateActivityQr(
            Long id,
            ActivityQrUpdateRequest request,
            LoginUser loginUser
    ) {
        Long userId = requireUserId(loginUser);
        Activity activity = activityRepository.findLockedById(id)
                .orElseThrow(() -> new BusinessException("Activity not found."));
        requireCreator(activity, userId);
        if (!STATUS_PUBLISHED.equals(activity.getStatus())
                || !activity.getEndAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException("活动已结束，不能更换群二维码。");
        }

        activity.setInviteQrUrl(request.getInviteQrUrl().trim());
        activity.setQrExpiresAt(LocalDateTime.now().plusDays(request.getRemainingDays()));
        activity.setQrReminderSentAt(null);
        Activity saved = activityRepository.save(activity);
        notifyRegisteredParticipants(
                saved,
                Notification.TYPE_ACTIVITY_QR_UPDATED,
                "群二维码已更新",
                "您报名的「" + saved.getTitle() + "」群二维码已更新，请前往主页—活动中查看。"
        );
        return toDetailVO(saved, null, true, false, true);
    }

    @Override
    @Transactional
    public void remindCreatorToUpdateQr(Long id, LoginUser loginUser) {
        Long userId = requireUserId(loginUser);
        Activity activity = activityRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Activity not found."));
        if (userId.equals(activity.getCreatorUserId())) {
            throw new BusinessException("发起者无需提醒自己更换二维码。");
        }
        if (!STATUS_PUBLISHED.equals(activity.getStatus())
                || activity.getEndAt() == null
                || !activity.getEndAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException("活动已结束，不能发送提醒。");
        }

        ActivityRegistration registration = activityRegistrationRepository.findByActivityIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException("只有已通过的参与者可以提醒发起者。"));
        if (!CAPACITY_STATUSES.contains(registration.getStatus())) {
            throw new BusinessException("只有已通过的参与者可以提醒发起者。");
        }

        LocalDateTime cooldownStart = LocalDateTime.now().minusHours(QR_CHANGE_REMINDER_COOLDOWN_HOURS);
        long recentReminders = notificationRepository
                .countByUserIdAndTypeAndRelatedTypeAndRelatedIdAndCreatedAtAfter(
                        activity.getCreatorUserId(),
                        Notification.TYPE_ACTIVITY_QR_CHANGE_REQUESTED,
                        Notification.RELATED_ACTIVITY,
                        activity.getId(),
                        cooldownStart
                );
        if (recentReminders > 0) {
            throw new BusinessException("已经提醒过发起者，请稍后再查看。");
        }

        User participant = userRepository.findById(userId).orElse(null);
        String participantName = resolveAuthorName(participant);
        notificationService.createNotification(
                activity.getCreatorUserId(),
                Notification.TYPE_ACTIVITY_QR_CHANGE_REQUESTED,
                "有人提醒你更换群二维码",
                participantName + " 提醒你「" + activity.getTitle() + "」的群二维码可能无法识别，请及时更新。",
                Notification.RELATED_ACTIVITY,
                activity.getId()
        );
    }

    @Override
    @Transactional
    public ActivityRegistrationVO applyActivity(Long id, ActivityApplyRequest request, LoginUser loginUser) {
        Long userId = requireUserId(loginUser);
        String applicationText = trimApplicationText(request == null ? null : request.getApplicationText());
        Activity activity = activityRepository.findLockedById(id)
                .orElseThrow(() -> new BusinessException("Activity not found."));

        if (!STATUS_PUBLISHED.equals(activity.getStatus())) {
            throw new BusinessException("Activity is not available.");
        }
        if (userId.equals(activity.getCreatorUserId())) {
            throw new BusinessException("不能报名自己发起的活动。");
        }
        if (activity.getStartAt() == null || !activity.getStartAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException("活动已经开始，无法报名。");
        }

        Optional<ActivityRegistration> existing = activityRegistrationRepository.findByActivityIdAndUserId(id, userId);
        if (existing.isPresent()) {
            ActivityRegistration registration = existing.get();
            if (CONFLICT_STATUSES.contains(registration.getStatus())) {
                return toRegistrationVO(registration);
            }
        }

        if (activityRegistrationRepository.existsTimeConflict(
                userId,
                activity.getStartAt(),
                activity.getEndAt(),
                CONFLICT_STATUSES
        )) {
            throw new BusinessException("报名时间冲突");
        }

        if (existing.isPresent()) {
            ActivityRegistration registration = existing.get();
            registration.setStatus(ActivityRegistration.STATUS_PENDING);
            registration.setNoticeCode(ActivityRegistration.NOTICE_APPLICATION_SUBMITTED);
            registration.setReviewedAt(null);
            registration.setReviewedBy(null);
            registration.setApprovedAt(null);
            registration.setJoinedGroupAt(null);
            registration.setApplicationText(applicationText);
            registration.setCancellationReasonType(null);
            registration.setCancellationReasonText(null);
            registration.setCancelledAt(null);
            registration = activityRegistrationRepository.save(registration);
            notifyRegistrationSubmitted(activity, registration, userId);
            return toRegistrationVO(registration);
        }

        ActivityRegistration registration = ActivityRegistration.builder()
                .userId(userId)
                .activityId(activity.getId())
                .status(ActivityRegistration.STATUS_PENDING)
                .noticeCode(ActivityRegistration.NOTICE_APPLICATION_SUBMITTED)
                .applicationText(applicationText)
                .build();

        registration = activityRegistrationRepository.save(registration);
        notifyRegistrationSubmitted(activity, registration, userId);
        return toRegistrationVO(registration);
    }

    @Override
    @Transactional
    public ActivityRegistrationVO joinGroup(Long id, LoginUser loginUser) {
        Long userId = requireUserId(loginUser);
        Activity activity = activityRepository.findLockedById(id)
                .orElseThrow(() -> new BusinessException("Activity not found."));

        if (!canOpenActivityQr(activity, LocalDateTime.now())) {
            throw new BusinessException("Activity group QR is not available.");
        }

        ActivityRegistration registration = activityRegistrationRepository.findByActivityIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException("Registration not found."));

        if (!ActivityRegistration.STATUS_APPROVED.equals(registration.getStatus())
                && !ActivityRegistration.STATUS_JOINED_GROUP.equals(registration.getStatus())) {
            throw new BusinessException("Registration has not been approved.");
        }

        if (!ActivityRegistration.STATUS_JOINED_GROUP.equals(registration.getStatus())) {
            registration.setStatus(ActivityRegistration.STATUS_JOINED_GROUP);
            registration.setNoticeCode(ActivityRegistration.NOTICE_JOINED_GROUP);
            registration.setJoinedGroupAt(LocalDateTime.now());
            registration = activityRegistrationRepository.save(registration);
        }

        return toRegistrationVO(registration);
    }

    @Override
    @Transactional
    public ActivityRegistrationVO cancelRegistration(
            Long id,
            ActivityCancelRegistrationRequest request,
            LoginUser loginUser
    ) {
        Long userId = requireUserId(loginUser);
        Activity activity = activityRepository.findLockedById(id)
                .orElseThrow(() -> new BusinessException("Activity not found."));
        ActivityRegistration registration =
                activityRegistrationRepository.findByActivityIdAndUserId(id, userId)
                        .orElseThrow(() -> new BusinessException("未找到该活动的报名记录。"));

        boolean releasedCapacity = CAPACITY_STATUSES.contains(registration.getStatus());
        if (!releasedCapacity && !ActivityRegistration.STATUS_PENDING.equals(registration.getStatus())) {
            throw new BusinessException("当前报名状态不能取消。");
        }
        if (!LocalDateTime.now().isBefore(activity.getStartAt().minusHours(12))) {
            throw new BusinessException("仅可在活动开始前至少12小时取消报名。");
        }

        String reasonType = request.getReasonType().trim();
        String reasonLabel = CANCELLATION_REASON_LABELS.get(reasonType);
        if (reasonLabel == null) {
            throw new BusinessException("请选择有效的取消原因。");
        }
        String reasonText = trimToNull(request.getReasonText());
        if ("other".equals(reasonType) && reasonText == null) {
            throw new BusinessException("请填写其他取消原因。");
        }
        if (reasonText != null && reasonText.length() > 100) {
            throw new BusinessException("取消原因最多100字。");
        }

        registration.setStatus(ActivityRegistration.STATUS_CANCELLED);
        registration.setNoticeCode(ActivityRegistration.NOTICE_CANCELLED);
        registration.setCancellationReasonType(reasonType);
        registration.setCancellationReasonText(reasonText);
        registration.setCancelledAt(LocalDateTime.now());
        ActivityRegistration saved = activityRegistrationRepository.save(registration);

        User participant = userRepository.findById(userId).orElse(null);
        String participantName = resolveAuthorName(participant);
        String reason = reasonLabel + (reasonText == null ? "" : "：" + reasonText);
        notificationService.createNotification(
                activity.getCreatorUserId(),
                Notification.TYPE_REGISTRATION_CANCELLED_CREATOR,
                "参与者取消报名",
                participantName + " 已取消「" + activity.getTitle() + "」的报名。原因：" + reason,
                Notification.RELATED_ACTIVITY,
                activity.getId()
        );
        if (releasedCapacity) {
            notifyEarliestPendingApplicant(activity);
        }
        return toRegistrationVO(saved);
    }

    @Override
    @Transactional
    public ActivityRegistrationVO approveRegistration(Long registrationId, LoginUser loginUser) {
        return reviewRegistration(
                registrationId,
                loginUser,
                ActivityRegistration.STATUS_APPROVED,
                ActivityRegistration.NOTICE_APPROVED
        );
    }

    @Override
    @Transactional
    public ActivityRegistrationVO rejectRegistration(Long registrationId, LoginUser loginUser) {
        return reviewRegistration(
                registrationId,
                loginUser,
                ActivityRegistration.STATUS_REJECTED,
                ActivityRegistration.NOTICE_REJECTED
        );
    }

    @Override
    @Transactional
    public ActivityFavoriteVO updateFavorite(
            Long id,
            ActivityFavoriteRequest request,
            LoginUser loginUser
    ) {
        Long userId = requireUserId(loginUser);
        Activity activity = activityRepository.findLockedById(id)
                .orElseThrow(() -> new BusinessException("Activity not found."));
        boolean favorited = request != null && Boolean.TRUE.equals(request.getFavorited());
        Optional<ActivityFavorite> existing = activityFavoriteRepository.findByUserIdAndActivityId(userId, id);
        int favoriteCount = activity.getFavoriteCount() == null ? 0 : activity.getFavoriteCount();

        if (favorited && existing.isEmpty()) {
            activityFavoriteRepository.save(ActivityFavorite.builder()
                    .userId(userId)
                    .activityId(id)
                    .build());
            favoriteCount += 1;
        } else if (!favorited && existing.isPresent()) {
            activityFavoriteRepository.delete(existing.get());
            favoriteCount = Math.max(0, favoriteCount - 1);
        }

        activity.setFavoriteCount(favoriteCount);
        activityRepository.save(activity);

        return ActivityFavoriteVO.builder()
                .id(id)
                .favorited(favorited)
                .favoriteCount(favoriteCount)
                .build();
    }

    @Override
    @Transactional
    public ActivityReportVO reportActivity(
            Long id,
            ActivityReportRequest request,
            LoginUser loginUser
    ) {
        Long reporterUserId = requireUserId(loginUser);
        Activity activity = activityRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Activity not found."));
        Long reportedUserId = activity.getCreatorUserId();
        if (reportedUserId == null) {
            throw new BusinessException("Activity creator not found.");
        }
        if (reporterUserId.equals(reportedUserId)) {
            throw new BusinessException("You cannot report your own activity.");
        }
        if (activityReportRepository.existsByReporterUserIdAndActivityId(reporterUserId, id)) {
            throw new BusinessException("You have already reported this activity.");
        }

        String reason = request == null ? "" : request.getReason();
        reason = reason == null ? "" : reason.trim();
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException("Please provide a report reason.");
        }
        if (reason.length() > 300) {
            throw new BusinessException("Report reason can be at most 300 characters.");
        }

        ActivityReport report = activityReportRepository.save(ActivityReport.builder()
                .reporterUserId(reporterUserId)
                .activityId(id)
                .reportedUserId(reportedUserId)
                .reason(reason)
                .status("PENDING")
                .build());

        notificationService.createNotification(
                reporterUserId,
                Notification.TYPE_REPORT_RECEIVED,
                "举报已提交",
                "我们已收到你对「" + activity.getTitle() + "」的举报，管理员处理后会通知你结果。",
                Notification.RELATED_ACTIVITY,
                activity.getId()
        );

        return ActivityReportVO.builder()
                .id(report.getId())
                .status(report.getStatus())
                .build();
    }

    private List<ActivityPostVO> toPostVOs(List<Activity> activities, Long userId) {
        if (activities == null || activities.isEmpty()) {
            return List.of();
        }
        List<Long> activityIds = activities.stream().map(Activity::getId).toList();
        Set<Long> favoriteIds = activityFavoriteRepository.findActivityIdsByUserIdAndActivityIdIn(
                userId,
                activityIds
        );
        Map<Long, Long> capacityCounts = activityRegistrationRepository
                .findByActivityIdInAndStatusIn(activityIds, CAPACITY_STATUSES)
                .stream()
                .collect(Collectors.groupingBy(ActivityRegistration::getActivityId, Collectors.counting()));
        Map<Long, String> registrationStatuses = activityRegistrationRepository
                .findByUserIdAndActivityIdIn(userId, activityIds)
                .stream()
                .collect(Collectors.toMap(
                        ActivityRegistration::getActivityId,
                        ActivityRegistration::getStatus,
                        (first, ignored) -> first
                ));
        return activities.stream()
                .map(activity -> {
                    ActivityPostVO post = toPostVO(
                            activity,
                            favoriteIds.contains(activity.getId()),
                            capacityCounts.getOrDefault(activity.getId(), 0L).intValue()
                    );
                    post.setRegistrationStatus(registrationStatuses.get(activity.getId()));
                    return post;
                })
                .toList();
    }

    private ActivityPostVO toPostVO(Activity activity, boolean favorited) {
        return toPostVO(activity, favorited, capacityCount(activity.getId()));
    }

    private ActivityPostVO toPostVO(Activity activity, boolean favorited, int joinedCount) {
        int totalCount = activity.getRecruitCount() == null ? 0 : activity.getRecruitCount();
        return ActivityPostVO.builder()
                .id(activity.getId())
                .title(activity.getTitle())
                .authorName(activity.getAuthorName())
                .coverUrl(activity.getCoverUrl())
                .avatarUrl(activity.getAvatarUrl())
                .favoriteCount(activity.getFavoriteCount())
                .favorited(favorited)
                .progressPct(calculateCapacityProgressPct(joinedCount, totalCount))
                .joinedCount(joinedCount)
                .totalCount(totalCount)
                .startAt(toEpochMillis(activity.getStartAt()))
                .endAt(toEpochMillis(activity.getEndAt()))
                .progressGif(activity.getProgressGif())
                .build();
    }

    private ActivityDetailVO toDetailVO(
            Activity activity,
            ActivityRegistration registration,
            boolean isCreator,
            boolean favorited,
            boolean canExposeQr
    ) {
        int joinedCount = activity.getArchivedParticipantCount() == null
                ? capacityCount(activity.getId())
                : activity.getArchivedParticipantCount().intValue();
        int totalCount = activity.getRecruitCount() == null ? 0 : activity.getRecruitCount();
        List<String> images = parseStringList(activity.getImageUrlsJson());
        if (images.isEmpty() && StringUtils.hasText(activity.getCoverUrl())) {
            images = List.of(activity.getCoverUrl());
        }
        List<String> tags = parseStringList(activity.getTagsJson());
        Double organizerRating = activity.getCreatorUserId() == null
                ? null
                : activityReviewRepository.findRecentAverageActivityRatingByCreatorUserId(
                        activity.getCreatorUserId(),
                        ActivityReview.TARGET_ACTIVITY,
                        LocalDateTime.now().minusDays(30)
                );
        User organizer = activity.getCreatorUserId() == null
                ? null
                : userRepository.findById(activity.getCreatorUserId()).orElse(null);
        String authorMotto = organizer != null && StringUtils.hasText(organizer.getMotto())
                ? organizer.getMotto().trim()
                : DEFAULT_PROFILE_MOTTO;
        List<String> authorTags = organizer == null
                ? List.of(DEFAULT_PROFILE_TAG)
                : parseStringList(organizer.getTagsJson());
        if (authorTags.isEmpty()) {
            authorTags = List.of(DEFAULT_PROFILE_TAG);
        }

        return ActivityDetailVO.builder()
                .id(activity.getId())
                .title(activity.getTitle())
                .content(activity.getContent())
                .authorName(activity.getAuthorName())
                .authorUserId(activity.getCreatorUserId())
                .authorAvatarUrl(activity.getAvatarUrl())
                .organizerRating(organizerRating)
                .authorMotto(authorMotto)
                .authorTags(authorTags)
                .coverUrl(activity.getCoverUrl())
                .imageUrls(images)
                .tags(tags)
                .tagIds(parseLongList(activity.getTagIdsJson()))
                .startAt(toEpochMillis(activity.getStartAt()))
                .endAt(toEpochMillis(activity.getEndAt()))
                .joinedCount(joinedCount)
                .totalCount(totalCount)
                .full(totalCount > 0 && joinedCount >= totalCount)
                .isCreator(isCreator)
                .favoriteCount(activity.getFavoriteCount())
                .favorited(favorited)
                .locationText(activity.getLocationText())
                .mapImageUrl(activity.getMapImageUrl())
                .inviteQrUrl(canExposeQr && canViewInviteQr(isCreator, registration)
                        ? activity.getInviteQrUrl() : null)
                .qrExpiresAt(toEpochMillis(activity.getQrExpiresAt()))
                .editCount(activity.getEditCount() == null ? 0 : activity.getEditCount())
                .canEdit(isCreator && canEdit(activity))
                .editBlockedReason(isCreator ? resolveEditBlockedReason(activity) : "not_creator")
                .registrationStatus(registration == null ? null : registration.getStatus())
                .noticeCode(registration == null ? null : registration.getNoticeCode())
                .build();
    }

    private void requireCreator(Activity activity, Long userId) {
        if (activity.getCreatorUserId() == null || !activity.getCreatorUserId().equals(userId)) {
            throw new BusinessException("只有活动发起者可以执行此操作。");
        }
    }

    private boolean canEdit(Activity activity) {
        return resolveEditBlockedReason(activity) == null;
    }

    private String resolveEditBlockedReason(Activity activity) {
        if (!STATUS_PUBLISHED.equals(activity.getStatus())) {
            return "not_available";
        }
        if (activity.getEndAt() != null && LocalDateTime.now().isAfter(activity.getEndAt())) {
            return "ended";
        }
        return null;
    }

    private boolean canViewInviteQr(boolean isCreator, ActivityRegistration registration) {
        return isCreator || (registration != null && CAPACITY_STATUSES.contains(registration.getStatus()));
    }

    private boolean textChanged(String before, String after) {
        return !Objects.equals(trimToNull(before), trimToNull(after));
    }

    private boolean isAppendOnly(String before, String after) {
        String original = before == null ? "" : before.trim();
        String updated = after == null ? "" : after.trim();
        return updated.equals(original) || updated.startsWith(original);
    }

    private boolean canOpenActivityQr(Activity activity, LocalDateTime now) {
        return STATUS_PUBLISHED.equals(activity.getStatus())
                && activity.getEndAt() != null
                && activity.getEndAt().isAfter(now)
                && StringUtils.hasText(activity.getInviteQrUrl())
                && activity.getQrExpiresAt() != null
                && activity.getQrExpiresAt().isAfter(now);
    }

    private void notifyRegisteredParticipants(
            Activity activity,
            String type,
            String title,
            String content
    ) {
        activityRegistrationRepository.findByActivityIdAndStatusIn(activity.getId(), CONFLICT_STATUSES)
                .stream()
                .map(ActivityRegistration::getUserId)
                .distinct()
                .forEach(userId -> notificationService.createNotification(
                        userId,
                        type,
                        title,
                        content,
                        Notification.RELATED_ACTIVITY,
                        activity.getId()
                ));
    }

    private ActivityRegistrationVO reviewRegistration(
            Long registrationId,
            LoginUser loginUser,
            String targetStatus,
            int noticeCode
    ) {
        Long reviewerId = requireUserId(loginUser);
        ActivityRegistration registration = activityRegistrationRepository.findById(registrationId)
                .orElseThrow(() -> new BusinessException("Registration not found."));
        Activity activity = activityRepository.findLockedById(registration.getActivityId())
                .orElseThrow(() -> new BusinessException("Activity not found."));

        if (!reviewerId.equals(activity.getCreatorUserId())) {
            throw new BusinessException("Only activity creator can review this registration.");
        }
        if (!ActivityRegistration.STATUS_PENDING.equals(registration.getStatus())) {
            throw new BusinessException("Registration has already been reviewed.");
        }
        if (!STATUS_PUBLISHED.equals(activity.getStatus())) {
            throw new BusinessException("活动当前不可审核。");
        }
        if (activity.getStartAt() == null || !activity.getStartAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException("活动已经开始，不能继续审核报名。");
        }
        if (ActivityRegistration.STATUS_APPROVED.equals(targetStatus) && isFull(activity)) {
            registration.setNoticeCode(ActivityRegistration.NOTICE_WAITING_FULL);
            registration = activityRegistrationRepository.save(registration);
            notificationService.createNotification(
                    registration.getUserId(),
                    Notification.TYPE_REGISTRATION_WAITING_FULL,
                    "活动名额已满",
                    "「" + activity.getTitle() + "」当前名额已满，你的报名仍在等待审核；有名额释放时会优先通知最早报名的人。",
                    Notification.RELATED_ACTIVITY,
                    activity.getId()
            );
            return toRegistrationVO(registration);
        }

        registration.setStatus(targetStatus);
        registration.setNoticeCode(noticeCode);
        registration.setReviewedBy(reviewerId);
        registration.setReviewedAt(LocalDateTime.now());
        if (ActivityRegistration.STATUS_APPROVED.equals(targetStatus)) {
            registration.setApprovedAt(LocalDateTime.now());
        }

        registration = activityRegistrationRepository.save(registration);
        notifyRegistrationReviewed(activity, registration, targetStatus);
        if (ActivityRegistration.STATUS_APPROVED.equals(targetStatus)
                && activity.getRecruitCount() != null
                && capacityCount(activity.getId()) >= activity.getRecruitCount()) {
            createNotificationOnce(
                    activity.getCreatorUserId(),
                    Notification.TYPE_ACTIVITY_FULL,
                    "活动已满员",
                    "你发起的「" + activity.getTitle() + "」已达到招募人数上限。",
                    activity.getId()
            );
        }
        return toRegistrationVO(registration);
    }

    private void notifyEarliestPendingApplicant(Activity activity) {
        if (!STATUS_PUBLISHED.equals(activity.getStatus())
                || activity.getStartAt() == null
                || !activity.getStartAt().isAfter(LocalDateTime.now())
                || isFull(activity)) {
            return;
        }
        activityRegistrationRepository
                .findFirstByActivityIdAndStatusOrderByCreatedAtAsc(
                        activity.getId(),
                        ActivityRegistration.STATUS_PENDING
                )
                .ifPresent(registration -> notificationService.createNotification(
                        registration.getUserId(),
                        Notification.TYPE_ACTIVITY_SLOT_AVAILABLE,
                        "活动有名额释放",
                        "「" + activity.getTitle() + "」刚刚释放了一个名额。你是当前最早报名的待审核参与者，请继续等待发起者审核。",
                        Notification.RELATED_ACTIVITY,
                        activity.getId()
                ));
    }

    private void createNotificationOnce(
            Long userId,
            String type,
            String title,
            String content,
            Long activityId
    ) {
        if (notificationRepository.existsByUserIdAndTypeAndRelatedTypeAndRelatedId(
                userId, type, Notification.RELATED_ACTIVITY, activityId)) {
            return;
        }
        notificationService.createNotification(
                userId, type, title, content, Notification.RELATED_ACTIVITY, activityId);
    }

    private void notifyRegistrationSubmitted(Activity activity, ActivityRegistration registration, Long applicantUserId) {
        User applicant = userRepository.findById(applicantUserId).orElse(null);
        String applicantName = resolveAuthorName(applicant);
        String activityTitle = activity.getTitle();

        notificationService.createNotification(
                activity.getCreatorUserId(),
                Notification.TYPE_REGISTRATION_SUBMITTED_CREATOR,
                "有人报名了你的活动",
                applicantName + " 想加入「" + activityTitle + "」，去审核一下吧。",
                Notification.RELATED_PENDING_REVIEW,
                registration.getId()
        );

        notificationService.createNotification(
                applicantUserId,
                Notification.TYPE_REGISTRATION_SUBMITTED_USER,
                "报名已提交",
                "你已提交「" + activityTitle
                        + "」报名申请，等待发起者审核。审核通过后，可在活动开始前至少12小时取消报名。",
                Notification.RELATED_ACTIVITY,
                activity.getId()
        );
    }

    private void notifyRegistrationReviewed(Activity activity, ActivityRegistration registration, String targetStatus) {
        boolean approved = ActivityRegistration.STATUS_APPROVED.equals(targetStatus);

        notificationService.createNotification(
                registration.getUserId(),
                approved ? Notification.TYPE_REGISTRATION_APPROVED : Notification.TYPE_REGISTRATION_REJECTED,
                approved ? "报名已通过" : "报名未通过",
                approved
                        ? "你已通过「" + activity.getTitle()
                                + "」报名，可前往主页—活动中查看。如无法参加，请在活动开始前至少12小时取消报名。"
                        : "你报名的「" + activity.getTitle() + "」未通过审核。",
                Notification.RELATED_ACTIVITY,
                activity.getId()
        );
    }

    private ActivityRegistrationVO toRegistrationVO(ActivityRegistration registration) {
        return ActivityRegistrationVO.builder()
                .id(registration.getId())
                .activityId(registration.getActivityId())
                .status(registration.getStatus())
                .applicationText(registration.getApplicationText())
                .noticeCode(registration.getNoticeCode())
                .build();
    }

    private String trimApplicationText(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        if (trimmed.length() > 100) {
            throw new BusinessException("报名申请不能超过100字");
        }
        return trimmed;
    }

    private boolean isFull(Activity activity) {
        Integer recruitCount = activity.getRecruitCount();
        return recruitCount != null && recruitCount > 0 && capacityCount(activity.getId()) >= recruitCount;
    }

    private int capacityCount(Long activityId) {
        return (int) activityRegistrationRepository.countByActivityIdAndStatusIn(activityId, CAPACITY_STATUSES);
    }

    private Long requireUserId(LoginUser loginUser) {
        if (loginUser == null || loginUser.userId() == null) {
            throw new BusinessException("Please login first.");
        }
        return loginUser.userId();
    }

    private Integer calculateProgressPct(Activity activity) {
        long start = toEpochMillis(activity.getStartAt());
        long end = toEpochMillis(activity.getEndAt());
        long now = System.currentTimeMillis();

        if (end <= start) {
            return 0;
        }
        if (now <= start) {
            return 100;
        }
        if (now >= end) {
            return 0;
        }

        return (int) Math.round((end - now) * 100.0 / (end - start));
    }

    private Integer calculateCapacityProgressPct(int joinedCount, int totalCount) {
        if (totalCount <= 0) {
            return 0;
        }
        return Math.min(100, Math.max(0, (int) Math.round(joinedCount * 100.0 / totalCount)));
    }

    private Long toEpochMillis(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private LocalDateTime toLocalDateTime(Long epochMillis) {
        if (epochMillis == null) {
            throw new BusinessException("Activity time is required.");
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }

    private List<String> sanitizeList(List<String> values, int maxSize) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed == null || result.contains(trimmed)) {
                continue;
            }
            result.add(trimmed);
            if (result.size() >= maxSize) {
                break;
            }
        }
        return result;
    }

    private List<Long> sanitizeTagIds(List<Long> values, int maxSize) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && value > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .limit(maxSize)
                .toList();
    }

    private List<Long> resolveRequestTagIds(ActivityCreateRequest request) {
        if (request.getTagId() != null) {
            return List.of(request.getTagId());
        }
        return request.getTagIds();
    }

    private List<String> resolveTagNames(List<Long> tagIds) {
        List<Tag> tags = tagRepository.findByIdInAndEnabledTrue(tagIds);
        Map<Long, Tag> tagById = tags.stream()
                .collect(Collectors.toMap(Tag::getId, Function.identity()));

        if (tagById.size() != tagIds.size()) {
            throw new BusinessException("Activity tag does not exist or is disabled.");
        }

        return tagIds.stream()
                .map(id -> tagById.get(id).getName())
                .toList();
    }

    private List<Long> resolveSearchTagIds(List<String> tagNames) {
        List<String> names = sanitizeList(tagNames, 10);
        if (names.isEmpty()) {
            return List.of();
        }
        return tagRepository.findByNameInAndEnabledTrue(names).stream()
                .map(Tag::getId)
                .toList();
    }

    private List<Long> resolveSearchTagIds(String tagsText) {
        if (!StringUtils.hasText(tagsText)) {
            return List.of();
        }
        return resolveSearchTagIds(List.of(tagsText.split(",")));
    }

    private String firstText(List<String> values) {
        if (values == null) {
            return null;
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private String resolveAuthorName(User creator) {
        if (creator == null) {
            return "MeetFun User";
        }
        if (StringUtils.hasText(creator.getNickname())) {
            return creator.getNickname().trim();
        }
        String openid = creator.getOpenid();
        if (StringUtils.hasText(openid) && openid.length() > 6) {
            return "User " + openid.substring(openid.length() - 6);
        }
        return "MeetFun User";
    }

    private Integer normalizeRecruitCount(Integer recruitCount) {
        if (recruitCount == null || recruitCount < 1) {
            return null;
        }
        return Math.min(recruitCount, 9999);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? List.of() : value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("Failed to serialize activity data.");
        }
    }

    private List<String> parseStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<String> values = objectMapper.readValue(
                    json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
            return values.stream()
                    .filter(StringUtils::hasText)
                    .toList();
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private List<Long> parseLongList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {});
        } catch (JsonProcessingException ignored) {
            return List.of();
        }
    }

    private String normalizeRange(String range) {
        if (!StringUtils.hasText(range)) {
            return "day";
        }
        return switch (range) {
            case "week", "month" -> range;
            default -> "day";
        };
    }

    private int normalizePage(Integer page) {
        if (page == null || page < 1) {
            return 1;
        }
        return page;
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return 20;
        }
        return Math.min(pageSize, 50);
    }
}
