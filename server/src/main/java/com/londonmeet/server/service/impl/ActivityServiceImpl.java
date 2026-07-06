package com.londonmeet.server.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.londonmeet.common.exception.BusinessException;
import com.londonmeet.pojo.dto.request.ActivityApplyRequest;
import com.londonmeet.pojo.dto.request.ActivityCancelRegistrationRequest;
import com.londonmeet.pojo.dto.request.ActivityCancelRequest;
import com.londonmeet.pojo.dto.request.ActivityCreateRequest;
import com.londonmeet.pojo.dto.request.ActivityEventRequest;
import com.londonmeet.pojo.dto.request.ActivityFavoriteRequest;
import com.londonmeet.pojo.dto.request.ActivityQueryRequest;
import com.londonmeet.pojo.dto.request.ActivityQrUpdateRequest;
import com.londonmeet.pojo.dto.request.ActivityRegistrationReviewRequest;
import com.londonmeet.pojo.dto.request.ActivityReportRequest;
import com.londonmeet.pojo.dto.request.ActivitySearchRequest;
import com.londonmeet.pojo.dto.request.ActivityUpdateRequest;
import com.londonmeet.pojo.entity.Activity;
import com.londonmeet.pojo.entity.ActivityFavorite;
import com.londonmeet.pojo.entity.ActivityRegistration;
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
import com.londonmeet.pojo.vo.PendingReviewActivityVO;
import com.londonmeet.pojo.vo.PendingReviewVO;
import com.londonmeet.server.repository.ActivityRepository;
import com.londonmeet.server.repository.ActivityFavoriteRepository;
import com.londonmeet.server.repository.ActivityRegistrationRepository;
import com.londonmeet.server.repository.ActivityReportRepository;
import com.londonmeet.server.repository.TagRepository;
import com.londonmeet.server.repository.UserRepository;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ActivityServiceImpl implements ActivityService {

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
    private final ActivityRepository activityRepository;
    private final ActivityFavoriteRepository activityFavoriteRepository;
    private final ActivityRegistrationRepository activityRegistrationRepository;
    private final ActivityReportRepository activityReportRepository;
    private final TagRepository tagRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;

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
        if (!endAt.isAfter(startAt)) {
            throw new BusinessException("Activity end time must be after start time.");
        }

        List<String> imageUrls = sanitizeList(request.getImageUrls(), 4);
        String coverUrl = firstText(imageUrls);
        if (!StringUtils.hasText(coverUrl)) {
            throw new BusinessException("At least one activity image is required.");
        }

        List<Long> tagIds = sanitizeTagIds(resolveRequestTagIds(request), 1);
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
                .endAt(endAt)
                .tagId(tagId)
                .tagIdsJson(toJson(tagIds))
                .tagsJson(toJson(tagNames))
                .recruitCount(normalizeRecruitCount(request.getRecruitCount()))
                .locationText(request.getLocationText().trim())
                .mapImageUrl(trimToNull(request.getMapImageUrl()))
                .imageUrlsJson(toJson(imageUrls))
                .inviteQrUrl(trimToNull(request.getInviteQrUrl()))
                .status(STATUS_PUBLISHED)
                .build();

        return toPostVO(activityRepository.save(activity));
    }

    @Override
    @Transactional
    public ActivityPageVO listActivities(ActivityQueryRequest request, LoginUser loginUser) {
        String range = normalizeRange(request == null ? null : request.getRange());
        int page = normalizePage(request == null ? null : request.getPage());
        int pageSize = normalizePageSize(request == null ? null : request.getPageSize());
        List<Long> tagIds = sanitizeTagIds(request == null ? null : request.getTagIds(), 10);

        LocalDateTime now = LocalDateTime.now();

        LocalDate today = now.toLocalDate();
        LocalDateTime rangeEnd = switch (range) {
            case "week" -> today.plusWeeks(1).plusDays(1).atStartOfDay();
            case "month" -> today.plusMonths(1).plusDays(1).atStartOfDay();
            default -> today.plusDays(1).atStartOfDay();
        };

        PageRequest pageRequest = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Activity> result = tagIds.isEmpty()
                ? activityRepository.findByStatusAndEndAtAfterAndEndAtBefore(
                        STATUS_PUBLISHED,
                        now,
                        rangeEnd,
                        pageRequest
                )
                : activityRepository.findByStatusAndEndAtAfterAndEndAtBeforeAndAnyTagIn(
                        STATUS_PUBLISHED,
                        now,
                        rangeEnd,
                        tagIds,
                        toJson(tagIds),
                        pageRequest
                );

        List<ActivityPostVO> list = result.getContent().stream()
                .map(this::toPostVO)
                .toList();

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
                .list(result.getContent().stream().map(this::toPostVO).toList())
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
                .list(result.getContent().stream().map(this::toPostVO).toList())
                .page(page)
                .pageSize(pageSize)
                .hasMore(result.hasNext())
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

                    return PendingReviewVO.builder()
                            .registrationId(registration.getId())
                            .activityId(registration.getActivityId())
                            .userId(registration.getUserId())
                            .activityTitle(activity == null ? "" : activity.getTitle())
                            .nickname(user == null ? "MeetFun User" : resolveAuthorName(user))
                            .avatarUrl(user == null ? null : user.getAvatarUrl())
                            .appliedAt(toEpochMillis(registration.getCreatedAt()))
                            .build();
                })
                .toList();
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

        return toDetailVO(activity, registration.orElse(null), isCreator);
    }

    @Override
    @Transactional
    public ActivityRegistrationVO applyActivity(Long id, ActivityApplyRequest request, LoginUser loginUser) {
        Long userId = requireUserId(loginUser);
        Activity activity = activityRepository.findLockedById(id)
                .orElseThrow(() -> new BusinessException("Activity not found."));

        if (!STATUS_PUBLISHED.equals(activity.getStatus())) {
            throw new BusinessException("Activity is not available.");
        }
        if (activity.getEndAt() != null && !activity.getEndAt().isAfter(LocalDateTime.now())) {
            throw new BusinessException("Activity has ended.");
        }

        Optional<ActivityRegistration> existing = activityRegistrationRepository.findByActivityIdAndUserId(id, userId);
        if (existing.isPresent()) {
            ActivityRegistration registration = existing.get();
            if (CONFLICT_STATUSES.contains(registration.getStatus())) {
                return toRegistrationVO(registration);
            }
        }

        if (isFull(activity)) {
            throw new BusinessException("人员已满");
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
            registration.setJoinedGroupAt(null);
            registration = activityRegistrationRepository.save(registration);
            notifyRegistrationSubmitted(activity, registration, userId);
            return toRegistrationVO(registration);
        }

        ActivityRegistration registration = ActivityRegistration.builder()
                .userId(userId)
                .activityId(activity.getId())
                .status(ActivityRegistration.STATUS_PENDING)
                .noticeCode(ActivityRegistration.NOTICE_APPLICATION_SUBMITTED)
                .build();

        registration = activityRegistrationRepository.save(registration);
        notifyRegistrationSubmitted(activity, registration, userId);
        return toRegistrationVO(registration);
    }

    @Override
    @Transactional
    public ActivityRegistrationVO joinGroup(Long id, LoginUser loginUser) {
        Long userId = requireUserId(loginUser);
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
    public ActivityRegistrationVO approveRegistration(
            Long registrationId,
            ActivityRegistrationReviewRequest request,
            LoginUser loginUser
    ) {
        return reviewRegistration(
                registrationId,
                loginUser,
                ActivityRegistration.STATUS_APPROVED,
                ActivityRegistration.NOTICE_APPROVED
        );
    }

    @Override
    @Transactional
    public ActivityRegistrationVO rejectRegistration(
            Long registrationId,
            ActivityRegistrationReviewRequest request,
            LoginUser loginUser
    ) {
        return reviewRegistration(
                registrationId,
                loginUser,
                ActivityRegistration.STATUS_REJECTED,
                ActivityRegistration.NOTICE_REJECTED
        );
    }

    @Override
    @Transactional
    public ActivityFavoriteVO updateFavorite(Long id, ActivityFavoriteRequest request, LoginUser loginUser) {
        Long userId = requireUserId(loginUser);
        Activity activity = activityRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Activity not found."));

        boolean favorited = request != null && Boolean.TRUE.equals(request.getFavorited());
        Optional<ActivityFavorite> existing =
                activityFavoriteRepository.findByUserIdAndActivityId(userId, id);
        if (favorited && existing.isEmpty()) {
            activityFavoriteRepository.save(ActivityFavorite.builder()
                    .userId(userId)
                    .activityId(id)
                    .build());
        } else if (!favorited && existing.isPresent()) {
            activityFavoriteRepository.delete(existing.get());
        }

        int favoriteCount = activityFavoriteRepository.findByActivityId(id).size();
        activity.setFavoriteCount(favoriteCount);
        activityRepository.save(activity);
        return ActivityFavoriteVO.builder()
                .id(activity.getId())
                .favorited(favorited)
                .favoriteCount(favoriteCount)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ActivityPageVO listMyCreatedActivities(ActivityQueryRequest request, LoginUser loginUser) {
        Long userId = requireUserId(loginUser);
        int page = normalizePage(request == null ? null : request.getPage());
        int pageSize = normalizePageSize(request == null ? null : request.getPageSize());
        Page<Activity> result = activityRepository.findByCreatorUserIdAndStatusAndEndAtAfter(
                userId,
                STATUS_PUBLISHED,
                LocalDateTime.now(),
                PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.ASC, "startAt"))
        );
        return toPageVO(result, page, pageSize);
    }

    @Override
    @Transactional(readOnly = true)
    public ActivityPageVO listFavoriteActivities(ActivityQueryRequest request, LoginUser loginUser) {
        Long userId = requireUserId(loginUser);
        int page = normalizePage(request == null ? null : request.getPage());
        int pageSize = normalizePageSize(request == null ? null : request.getPageSize());
        Page<Activity> result = activityRepository.findActiveFavorites(
                userId,
                STATUS_PUBLISHED,
                LocalDateTime.now(),
                PageRequest.of(page - 1, pageSize)
        );
        return toPageVO(result, page, pageSize);
    }

    @Override
    @Transactional(readOnly = true)
    public ActivityPageVO listHistoryActivities(ActivityQueryRequest request, LoginUser loginUser) {
        Long userId = requireUserId(loginUser);
        List<Activity> activities = activityRepository.findRelatedEndedActivities(
                userId,
                STATUS_PUBLISHED,
                LocalDateTime.now(),
                CAPACITY_STATUSES
        );
        return ActivityPageVO.builder()
                .list(activities.stream().map(this::toPostVO).toList())
                .page(1)
                .pageSize(activities.size())
                .hasMore(false)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingReviewActivityVO> listPendingReviewActivities(LoginUser loginUser) {
        return List.of();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingReviewVO> listActivityReviewRegistrations(Long activityId, String status, LoginUser loginUser) {
        return listPendingReviews(loginUser).stream()
                .filter(item -> activityId == null || activityId.equals(item.getActivityId()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PendingReviewVO> listOrganizerBlacklist(Long activityId, LoginUser loginUser) {
        return List.of();
    }

    @Override
    @Transactional
    public ActivityDetailVO updateActivity(Long id, ActivityUpdateRequest request, LoginUser loginUser) {
        throw new BusinessException("Activity update is not available in this build.");
    }

    @Override
    @Transactional
    public ActivityDetailVO updateActivityQr(Long id, ActivityQrUpdateRequest request, LoginUser loginUser) {
        throw new BusinessException("Activity QR update is not available in this build.");
    }

    @Override
    @Transactional
    public ActivityDetailVO cancelActivity(Long id, ActivityCancelRequest request, LoginUser loginUser) {
        throw new BusinessException("Activity cancellation is not available in this build.");
    }

    @Override
    public void remindCreatorToUpdateQr(Long id, LoginUser loginUser) {
        throw new BusinessException("QR reminder is not available in this build.");
    }

    @Override
    @Transactional
    public ActivityRegistrationVO blacklistRegistration(
            Long registrationId,
            ActivityRegistrationReviewRequest request,
            LoginUser loginUser
    ) {
        return reviewRegistration(
                registrationId,
                loginUser,
                ActivityRegistration.STATUS_BLACKLISTED,
                ActivityRegistration.NOTICE_REJECTED
        );
    }

    @Override
    public void unblockApplicant(Long blacklistId, LoginUser loginUser) {
        throw new BusinessException("Blacklist unblock is not available in this build.");
    }

    @Override
    @Transactional
    public ActivityRegistrationVO cancelRegistration(
            Long id,
            ActivityCancelRegistrationRequest request,
            LoginUser loginUser
    ) {
        Long userId = requireUserId(loginUser);
        ActivityRegistration registration = activityRegistrationRepository.findByActivityIdAndUserId(id, userId)
                .orElseThrow(() -> new BusinessException("Registration not found."));
        registration.setStatus(ActivityRegistration.STATUS_CANCELLED);
        registration.setNoticeCode(ActivityRegistration.NOTICE_CANCELLED);
        registration.setCancelledAt(LocalDateTime.now());
        return toRegistrationVO(activityRegistrationRepository.save(registration));
    }

    @Override
    @Transactional
    public ActivityReportVO reportActivity(Long id, ActivityReportRequest request, LoginUser loginUser) {
        Long userId = requireUserId(loginUser);
        Activity activity = activityRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Activity not found."));
        if (activityReportRepository.existsByReporterUserIdAndActivityId(userId, id)) {
            throw new BusinessException("You have already reported this activity.");
        }
        ActivityReport report = activityReportRepository.save(ActivityReport.builder()
                .reporterUserId(userId)
                .activityId(id)
                .reportedUserId(activity.getCreatorUserId())
                .reason(request == null || !StringUtils.hasText(request.getReason()) ? "No reason" : request.getReason())
                .build());
        return ActivityReportVO.builder().id(report.getId()).status(report.getStatus()).build();
    }

    @Override
    public void recordEvents(ActivityEventRequest request, LoginUser loginUser) {
        // Analytics recording is intentionally a no-op in this build path.
    }

    private ActivityPageVO toPageVO(Page<Activity> result, int page, int pageSize) {
        return ActivityPageVO.builder()
                .list(result.getContent().stream().map(this::toPostVO).toList())
                .page(page)
                .pageSize(pageSize)
                .hasMore(result.hasNext())
                .build();
    }

    private ActivityPostVO toPostVO(Activity activity) {
        int joinedCount = capacityCount(activity.getId());
        int totalCount = activity.getRecruitCount() == null ? 0 : activity.getRecruitCount();
        return ActivityPostVO.builder()
                .id(activity.getId())
                .title(activity.getTitle())
                .authorName(activity.getAuthorName())
                .coverUrl(activity.getCoverUrl())
                .avatarUrl(activity.getAvatarUrl())
                .favoriteCount(activity.getFavoriteCount())
                .favorited(false)
                .progressPct(calculateProgressPct(activity))
                .joinedCount(joinedCount)
                .totalCount(totalCount)
                .tagIds(parseLongList(activity.getTagIdsJson()))
                .locationText(activity.getLocationText())
                .startAt(toEpochMillis(activity.getStartAt()))
                .endAt(toEpochMillis(activity.getEndAt()))
                .progressGif(activity.getProgressGif())
                .build();
    }

    private ActivityDetailVO toDetailVO(Activity activity, ActivityRegistration registration, boolean isCreator) {
        int joinedCount = capacityCount(activity.getId());
        int totalCount = activity.getRecruitCount() == null ? 0 : activity.getRecruitCount();
        List<String> images = parseStringList(activity.getImageUrlsJson());
        if (images.isEmpty() && StringUtils.hasText(activity.getCoverUrl())) {
            images = List.of(activity.getCoverUrl());
        }

        return ActivityDetailVO.builder()
                .id(activity.getId())
                .title(activity.getTitle())
                .content(activity.getContent())
                .authorName(activity.getAuthorName())
                .coverUrl(activity.getCoverUrl())
                .imageUrls(images)
                .startAt(toEpochMillis(activity.getStartAt()))
                .endAt(toEpochMillis(activity.getEndAt()))
                .joinedCount(joinedCount)
                .totalCount(totalCount)
                .full(totalCount > 0 && joinedCount >= totalCount)
                .isCreator(isCreator)
                .locationText(activity.getLocationText())
                .mapImageUrl(activity.getMapImageUrl())
                .inviteQrUrl(activity.getInviteQrUrl())
                .registrationStatus(registration == null ? null : registration.getStatus())
                .noticeCode(registration == null ? null : registration.getNoticeCode())
                .build();
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
        Activity activity = activityRepository.findById(registration.getActivityId())
                .orElseThrow(() -> new BusinessException("Activity not found."));

        if (!reviewerId.equals(activity.getCreatorUserId())) {
            throw new BusinessException("Only activity creator can review this registration.");
        }
        if (!ActivityRegistration.STATUS_PENDING.equals(registration.getStatus())) {
            throw new BusinessException("Registration has already been reviewed.");
        }

        registration.setStatus(targetStatus);
        registration.setNoticeCode(noticeCode);
        registration.setReviewedBy(reviewerId);
        registration.setReviewedAt(LocalDateTime.now());

        registration = activityRegistrationRepository.save(registration);
        notifyRegistrationReviewed(activity, registration, targetStatus);
        return toRegistrationVO(registration);
    }

    private void notifyRegistrationSubmitted(Activity activity, ActivityRegistration registration, Long applicantUserId) {
        User applicant = userRepository.findById(applicantUserId).orElse(null);
        String applicantName = resolveAuthorName(applicant);
        String activityTitle = activity.getTitle();

        notificationService.createNotification(
                activity.getCreatorUserId(),
                Notification.TYPE_REGISTRATION_SUBMITTED_CREATOR,
                "New registration",
                applicantName + " wants to join " + activityTitle + ". Please review it.",
                Notification.RELATED_ACTIVITY,
                activity.getId()
        );

        notificationService.createNotification(
                applicantUserId,
                Notification.TYPE_REGISTRATION_SUBMITTED_USER,
                "Registration submitted",
                "You have registered for " + activityTitle + ". Please wait for creator review.",
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
                        ? "你已通过「" + activity.getTitle() + "」报名，可以加入群聊了。"
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
                .noticeCode(registration.getNoticeCode())
                .build();
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
