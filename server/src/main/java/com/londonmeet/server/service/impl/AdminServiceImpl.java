package com.londonmeet.server.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.londonmeet.common.constant.JwtClaimsConstant;
import com.londonmeet.common.exception.BusinessException;
import com.londonmeet.common.properties.JwtProperties;
import com.londonmeet.common.utils.JwtUtil;
import com.londonmeet.pojo.dto.request.AdminLoginRequest;
import com.londonmeet.pojo.dto.request.AdminActionRequest;
import com.londonmeet.pojo.dto.request.AdminActivityTagsRequest;
import com.londonmeet.pojo.dto.request.AdminActivityUpdateRequest;
import com.londonmeet.pojo.dto.request.AdminTagRequest;
import com.londonmeet.pojo.dto.request.AdminNotificationRequest;
import com.londonmeet.pojo.dto.request.ReviewScoreRequest;
import com.londonmeet.pojo.entity.AdminAuditLog;
import com.londonmeet.pojo.entity.Activity;
import com.londonmeet.pojo.entity.ActivityRegistration;
import com.londonmeet.pojo.entity.ActivityReport;
import com.londonmeet.pojo.entity.ActivityReview;
import com.londonmeet.pojo.entity.User;
import com.londonmeet.pojo.entity.Tag;
import com.londonmeet.pojo.entity.UserFeedback;
import com.londonmeet.pojo.entity.Notification;
import com.londonmeet.pojo.vo.AdminActivityItemVO;
import com.londonmeet.pojo.vo.AdminActivityDetailVO;
import com.londonmeet.pojo.vo.AdminActivityPageVO;
import com.londonmeet.pojo.vo.AdminAuditLogVO;
import com.londonmeet.pojo.vo.AdminParticipantVO;
import com.londonmeet.pojo.vo.AdminReportItemVO;
import com.londonmeet.pojo.vo.AdminReportPageVO;
import com.londonmeet.pojo.vo.AdminUserItemVO;
import com.londonmeet.pojo.vo.AdminUserPageVO;
import com.londonmeet.pojo.vo.AdminDashboardVO;
import com.londonmeet.pojo.vo.AdminLoginVO;
import com.londonmeet.pojo.vo.AdminSettingsVO;
import com.londonmeet.pojo.vo.AdminTagVO;
import com.londonmeet.pojo.vo.AdminFeedbackItemVO;
import com.londonmeet.pojo.vo.AdminFeedbackPageVO;
import com.londonmeet.pojo.vo.AdminReviewItemVO;
import com.londonmeet.pojo.vo.AdminReviewPageVO;
import com.londonmeet.server.config.AdminProperties;
import com.londonmeet.server.config.GoogleMapsProperties;
import com.londonmeet.server.config.UploadProperties;
import com.londonmeet.server.repository.ActivityRegistrationRepository;
import com.londonmeet.server.repository.AdminAuditLogRepository;
import com.londonmeet.server.repository.ActivityReportRepository;
import com.londonmeet.server.repository.ActivityReviewRepository;
import com.londonmeet.server.repository.ActivityRepository;
import com.londonmeet.server.repository.UserRepository;
import com.londonmeet.server.repository.TagRepository;
import com.londonmeet.server.repository.UserFeedbackRepository;
import com.londonmeet.server.security.LoginUser;
import com.londonmeet.server.service.AdminService;
import com.londonmeet.server.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final Set<String> JOINED_STATUSES = Set.of(
            ActivityRegistration.STATUS_APPROVED,
            ActivityRegistration.STATUS_JOINED_GROUP
    );

    private final AdminProperties adminProperties;
    private final GoogleMapsProperties googleMapsProperties;
    private final JwtProperties jwtProperties;
    private final UploadProperties uploadProperties;
    private final UserRepository userRepository;
    private final ActivityRepository activityRepository;
    private final ActivityRegistrationRepository activityRegistrationRepository;
    private final ActivityReportRepository activityReportRepository;
    private final ActivityReviewRepository activityReviewRepository;
    private final AdminAuditLogRepository adminAuditLogRepository;
    private final TagRepository tagRepository;
    private final UserFeedbackRepository userFeedbackRepository;
    private final NotificationService notificationService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public AdminLoginVO login(AdminLoginRequest request) {
        String username = request == null ? "" : request.getUsername();
        String password = request == null ? "" : request.getPassword();
        username = username == null ? "" : username.trim();
        final String adminUsername = username;

        if (!adminProperties.getUsername().equals(adminUsername)
                || !adminProperties.getPassword().equals(password)) {
            throw new BusinessException("管理员账号或密码错误");
        }

        String openid = "admin:" + adminUsername;
        User admin = userRepository.findByOpenid(openid)
                .orElseGet(() -> User.builder()
                        .openid(openid)
                        .nickname(adminUsername)
                        .avatarUrl(uploadProperties.getDefaultAvatarUrl())
                        .coverUrl(uploadProperties.getDefaultCoverUrl())
                        .role(ROLE_ADMIN)
                        .status(STATUS_ACTIVE)
                        .build());
        admin.setNickname(adminUsername);
        admin.setRole(ROLE_ADMIN);
        admin.setStatus(STATUS_ACTIVE);
        admin.setLastLoginAt(LocalDateTime.now());
        admin = userRepository.save(admin);

        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, admin.getId());
        claims.put(JwtClaimsConstant.OPENID, admin.getOpenid());
        String token = JwtUtil.generateToken(
                jwtProperties.getSecretKey(),
                jwtProperties.getExpiration(),
                claims
        );

        return AdminLoginVO.builder()
                .token(token)
                .username(adminUsername)
                .role(ROLE_ADMIN)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminDashboardVO getDashboard(LoginUser loginUser) {
        requireAdmin(loginUser);
        LocalDateTime now = LocalDateTime.now();
        List<AdminDashboardVO.DailyCount> trend = new ArrayList<>();

        for (int offset = 6; offset >= 0; offset--) {
            LocalDate date = LocalDate.now().minusDays(offset);
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.plusDays(1).atStartOfDay();
            trend.add(AdminDashboardVO.DailyCount.builder()
                    .date(date.toString())
                    .count(activityRepository.countByCreatedAtBetween(start, end))
                    .build());
        }

        return AdminDashboardVO.builder()
                .userCount(userRepository.count())
                .activityCount(activityRepository.count())
                .registrationCount(activityRegistrationRepository.count())
                .pendingReportCount(activityReportRepository.countByStatus("PENDING"))
                .upcomingActivityCount(activityRepository.countByStartAtAfter(now))
                .ongoingActivityCount(
                        activityRepository.countByStartAtLessThanEqualAndEndAtAfter(now, now)
                )
                .endedActivityCount(activityRepository.countByEndAtLessThanEqual(now))
                .activityTrend(trend)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminSettingsVO getSettings(LoginUser loginUser) {
        requireAdmin(loginUser);
        Integer days = jdbcTemplate.queryForObject(
                "SELECT CAST(setting_value AS UNSIGNED) FROM system_settings WHERE setting_key = ?",
                Integer.class, "activity_detail_retention_days");
        return AdminSettingsVO.builder()
                .activityDetailRetentionDays(days == null ? 30 : days)
                .retentionDescription("活动结束后保留完整明细，超过30天后自动清理。")
                .exportDescription("报告不在服务器保存，点击下载时实时生成近30天Excel文件。")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportRecentReport(LoginUser loginUser) {
        requireAdmin(loginUser);
        LocalDateTime end = LocalDateTime.now();
        LocalDateTime start = end.minusDays(30);
        DateTimeFormatter dateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Sheet summary = workbook.createSheet("近30天汇总");
            addRow(summary, 0, headerStyle, "指标", "数据");
            int row = 1;
            row = addMetric(summary, row, "统计开始时间", start.format(dateTime));
            row = addMetric(summary, row, "统计结束时间", end.format(dateTime));
            row = addMetric(summary, row, "当前总用户数",
                    scalar("SELECT COUNT(*) FROM users WHERE role <> 'ADMIN'"));
            row = addMetric(summary, row, "近30天新增用户",
                    scalar("SELECT COUNT(*) FROM users WHERE role <> 'ADMIN' AND created_at >= ?", start));
            row = addMetric(summary, row, "当前数据库活动数",
                    scalar("SELECT COUNT(*) FROM activities"));
            row = addMetric(summary, row, "近30天新建活动",
                    scalar("SELECT COUNT(*) FROM activities WHERE created_at >= ?", start));
            row = addMetric(summary, row, "近30天结束活动",
                    scalar("SELECT COUNT(*) FROM activities WHERE end_at >= ? AND end_at <= ?", start, end));
            row = addMetric(summary, row, "近30天强制结束活动",
                    scalar("SELECT COUNT(*) FROM admin_audit_logs WHERE action_type = 'ACTIVITY_FORCE_END' AND created_at >= ?", start));
            row = addMetric(summary, row, "近30天报名总数",
                    scalar("SELECT COUNT(*) FROM activity_registrations WHERE created_at >= ?", start));
            row = addMetric(summary, row, "近30天通过人数",
                    scalar("SELECT COUNT(*) FROM activity_registrations WHERE created_at >= ? AND status IN ('approved','joined_group')", start));
            row = addMetric(summary, row, "近30天拒绝人数",
                    scalar("SELECT COUNT(*) FROM activity_registrations WHERE created_at >= ? AND status = 'rejected'", start));
            row = addMetric(summary, row, "近30天活动收藏数",
                    scalar("SELECT COUNT(*) FROM activity_favorites WHERE created_at >= ?", start));
            row = addMetric(summary, row, "近30天举报数",
                    scalar("SELECT COUNT(*) FROM activity_reports WHERE created_at >= ?", start));
            row = addMetric(summary, row, "近30天已处理举报",
                    scalar("SELECT COUNT(*) FROM activity_reports WHERE created_at >= ? AND status IN ('RESOLVED','DISMISSED')", start));
            row = addMetric(summary, row, "近30天待处理举报",
                    scalar("SELECT COUNT(*) FROM activity_reports WHERE created_at >= ? AND status = 'PENDING'", start));
            summary.setColumnWidth(0, 32 * 256);
            summary.setColumnWidth(1, 24 * 256);

            Sheet details = workbook.createSheet("活动明细");
            String[] headers = {
                    "活动ID", "活动标题", "发起人", "创建时间", "开始时间", "结束时间",
                    "状态", "招募人数", "报名数", "通过人数", "待审核", "收藏数", "举报数",
                    "地点", "最近管理操作", "操作理由"
            };
            addRow(details, 0, headerStyle, (Object[]) headers);
            List<Map<String, Object>> activities = jdbcTemplate.queryForList("""
                    SELECT a.id, a.title, a.author_name, a.created_at, a.start_at, a.end_at,
                           a.status, a.recruit_count, a.favorite_count, a.location_text,
                           (SELECT COUNT(*) FROM activity_registrations r WHERE r.activity_id = a.id) registration_count,
                           (SELECT COUNT(*) FROM activity_registrations r WHERE r.activity_id = a.id
                               AND r.status IN ('approved','joined_group')) joined_count,
                           (SELECT COUNT(*) FROM activity_registrations r WHERE r.activity_id = a.id
                               AND r.status = 'pending') pending_count,
                           (SELECT COUNT(*) FROM activity_reports p WHERE p.activity_id = a.id) report_count,
                           (SELECT l.action_type FROM admin_audit_logs l
                               WHERE l.target_type = 'ACTIVITY' AND l.target_id = a.id
                               ORDER BY l.created_at DESC LIMIT 1) governance_action,
                           (SELECT l.reason FROM admin_audit_logs l
                               WHERE l.target_type = 'ACTIVITY' AND l.target_id = a.id
                               ORDER BY l.created_at DESC LIMIT 1) governance_reason
                    FROM activities a
                    WHERE a.created_at >= ? OR a.end_at >= ?
                    ORDER BY a.created_at DESC
                    """, start, start);
            int detailRow = 1;
            for (Map<String, Object> activity : activities) {
                addRow(details, detailRow++, null,
                        activity.get("id"), activity.get("title"), activity.get("author_name"),
                        text(activity.get("created_at")), text(activity.get("start_at")), text(activity.get("end_at")),
                        activity.get("status"), activity.get("recruit_count"), activity.get("registration_count"),
                        activity.get("joined_count"), activity.get("pending_count"), activity.get("favorite_count"),
                        activity.get("report_count"), activity.get("location_text"),
                        activity.get("governance_action"), activity.get("governance_reason"));
            }
            for (int column = 0; column < headers.length; column++) {
                details.setColumnWidth(column, Math.min(column == 1 || column == 13 || column == 15 ? 32 : 18, 40) * 256);
            }
            details.createFreezePane(0, 1);
            details.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(0, detailRow - 1), 0, headers.length - 1));

            workbook.write(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new BusinessException("生成近30天Excel报告失败");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AdminActivityPageVO listActivities(
            String keyword,
            String status,
            Integer page,
            Integer pageSize,
            LoginUser loginUser
    ) {
        requireAdmin(loginUser);
        int normalizedPage = page == null || page < 1 ? 1 : page;
        int normalizedPageSize = pageSize == null || pageSize < 1
                ? 20
                : Math.min(pageSize, 100);
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        String normalizedStatus = status != null
                && Set.of("upcoming", "ongoing", "ended").contains(status)
                ? status
                : null;
        LocalDateTime now = LocalDateTime.now();

        Page<Activity> result = activityRepository.findAdminActivities(
                normalizedKeyword,
                normalizedStatus,
                now,
                PageRequest.of(
                        normalizedPage - 1,
                        normalizedPageSize,
                        Sort.by(Sort.Direction.DESC, "createdAt")
                )
        );

        List<Activity> activities = result.getContent();
        if (activities.isEmpty()) {
            return AdminActivityPageVO.builder()
                    .list(List.of())
                    .page(normalizedPage)
                    .pageSize(normalizedPageSize)
                    .total(result.getTotalElements())
                    .build();
        }

        List<Long> ids = activities.stream().map(Activity::getId).toList();
        Map<Long, List<ActivityRegistration>> registrations = activityRegistrationRepository
                .findByActivityIdIn(ids)
                .stream()
                .collect(Collectors.groupingBy(ActivityRegistration::getActivityId));
        Map<Long, Long> reportCounts = activityReportRepository.findByActivityIdIn(ids)
                .stream()
                .collect(Collectors.groupingBy(
                        ActivityReport::getActivityId,
                        Collectors.counting()
                ));

        List<AdminActivityItemVO> items = activities.stream()
                .map(activity -> {
                    List<ActivityRegistration> activityRegistrations =
                            registrations.getOrDefault(activity.getId(), List.of());
                    int joinedCount = (int) activityRegistrations.stream()
                            .filter(item -> JOINED_STATUSES.contains(item.getStatus()))
                            .count();
                    int pendingCount = (int) activityRegistrations.stream()
                            .filter(item -> ActivityRegistration.STATUS_PENDING.equals(item.getStatus()))
                            .count();
                    int recruitCount = activity.getRecruitCount() == null ? 0 : activity.getRecruitCount();

                    return AdminActivityItemVO.builder()
                            .id(activity.getId())
                            .title(activity.getTitle())
                            .creatorUserId(activity.getCreatorUserId())
                            .authorName(activity.getAuthorName())
                            .status(resolveStatus(activity, now))
                            .joinedCount(joinedCount)
                            .pendingCount(pendingCount)
                            .recruitCount(recruitCount)
                            .progressPct(recruitCount > 0
                                    ? Math.min(100, Math.round(joinedCount * 100f / recruitCount))
                                    : 0)
                            .startAt(toEpochMillis(activity.getStartAt()))
                            .endAt(toEpochMillis(activity.getEndAt()))
                            .locationText(activity.getLocationText())
                            .favoriteCount(activity.getFavoriteCount())
                            .reportCount(reportCounts.getOrDefault(activity.getId(), 0L))
                            .build();
                })
                .toList();

        return AdminActivityPageVO.builder()
                .list(items)
                .page(normalizedPage)
                .pageSize(normalizedPageSize)
                .total(result.getTotalElements())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminActivityDetailVO getActivityDetail(Long id, LoginUser loginUser) {
        requireAdmin(loginUser);
        Activity activity = activityRepository.findById(id)
                .orElseThrow(() -> new BusinessException("活动不存在"));
        return buildActivityDetail(activity);
    }

    @Override
    @Transactional
    public AdminActivityDetailVO updateActivityStatus(
            Long id,
            String action,
            AdminActionRequest request,
            LoginUser loginUser
    ) {
        requireAdmin(loginUser);
        requireAdminPassword(request == null ? null : request.getPassword());
        Activity activity = activityRepository.findById(id)
                .orElseThrow(() -> new BusinessException("活动不存在"));
        String reason = requireReason(request);
        Map<String, Object> before = activityAuditState(activity);

        switch (action) {
            case "hide" -> activity.setStatus("HIDDEN");
            case "restore" -> activity.setStatus("PUBLISHED");
            case "force-end" -> {
                if (activity.getEndAt().isAfter(LocalDateTime.now())) {
                    activity.setEndAt(LocalDateTime.now());
                }
            }
            default -> throw new BusinessException("不支持的活动操作");
        }
        activityRepository.save(activity);
        notifyAdminActivityAction(
                activity,
                "管理员已" + adminActivityActionLabel(action),
                "「" + activity.getTitle() + "」已由管理员" + adminActivityActionLabel(action)
                        + "。处理说明：" + reason
        );
        saveAudit(
                loginUser.userId(),
                "ACTIVITY_" + action.toUpperCase().replace('-', '_'),
                "ACTIVITY",
                id,
                reason,
                before,
                activityAuditState(activity)
        );
        return buildActivityDetail(activity);
    }

    @Override
    @Transactional
    public AdminActivityDetailVO updateActivity(
            Long id,
            AdminActivityUpdateRequest request,
            LoginUser loginUser
    ) {
        requireAdmin(loginUser);
        if (request == null) {
            throw new BusinessException("请填写活动修改内容");
        }
        requireAdminPassword(request.getPassword());

        String reason = request.getReason() == null ? "" : request.getReason().trim();
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException("请填写修改原因");
        }
        if (reason.length() > 500) {
            throw new BusinessException("修改原因最多 500 字");
        }

        String status = request.getStatus() == null ? "" : request.getStatus().trim().toUpperCase();
        if (!Set.of("PUBLISHED", "HIDDEN", "CANCELLED").contains(status)) {
            throw new BusinessException("活动状态无效");
        }

        LocalDateTime startAt = fromEpochMillis(request.getStartAt());
        LocalDateTime endAt = fromEpochMillis(request.getEndAt());
        if (startAt == null || endAt == null) {
            throw new BusinessException("请填写活动开始和结束时间");
        }
        if (!endAt.isAfter(startAt)) {
            throw new BusinessException("活动结束时间必须晚于开始时间");
        }

        String locationText = request.getLocationText() == null
                ? ""
                : request.getLocationText().trim();
        if (!StringUtils.hasText(locationText)) {
            throw new BusinessException("请填写活动地址");
        }
        if (locationText.length() > 500) {
            throw new BusinessException("活动地址最多 500 字");
        }

        Activity activity = activityRepository.findById(id)
                .orElseThrow(() -> new BusinessException("活动不存在"));
        Map<String, Object> before = activityAuditState(activity);

        boolean locationChanged = !locationText.equals(activity.getLocationText());
        activity.setStatus(status);
        activity.setStartAt(startAt);
        activity.setEndAt(endAt);
        activity.setLocationText(locationText);
        if (locationChanged) {
            activity.setMapImageUrl(buildMapImageUrl(locationText));
        }
        activityRepository.save(activity);
        notifyAdminActivityAction(
                activity,
                "活动信息已由管理员调整",
                "「" + activity.getTitle() + "」的状态、时间或地点已由管理员调整。处理说明：" + reason
        );

        Map<String, Object> after = activityAuditState(activity);
        saveAudit(
                loginUser.userId(),
                "ACTIVITY_UPDATE",
                "ACTIVITY",
                id,
                reason,
                before,
                after
        );
        return buildActivityDetail(activity);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminReportPageVO listReports(
            String status,
            Integer page,
            Integer pageSize,
            LoginUser loginUser
    ) {
        requireAdmin(loginUser);
        int p = normalizePage(page);
        int size = normalizePageSize(pageSize);
        Page<ActivityReport> result = StringUtils.hasText(status)
                ? activityReportRepository.findByStatusOrderByCreatedAtDesc(
                        status.trim().toUpperCase(), PageRequest.of(p - 1, size))
                : activityReportRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(p - 1, size));
        List<ActivityReport> reports = result.getContent();
        Set<Long> userIds = reports.stream()
                .flatMap(item -> java.util.stream.Stream.of(item.getReporterUserId(), item.getReportedUserId()))
                .collect(Collectors.toSet());
        Map<Long, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Activity> activities = activityRepository.findAllById(
                        reports.stream().map(ActivityReport::getActivityId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Activity::getId, Function.identity()));

        return AdminReportPageVO.builder()
                .list(reports.stream().map(report -> {
                    User reporter = users.get(report.getReporterUserId());
                    User reported = users.get(report.getReportedUserId());
                    Activity activity = activities.get(report.getActivityId());
                    return AdminReportItemVO.builder()
                            .id(report.getId())
                            .activityId(report.getActivityId())
                            .activityTitle(activity == null ? "活动已删除" : activity.getTitle())
                            .reporterUserId(report.getReporterUserId())
                            .reporterName(resolveName(reporter))
                            .reportedUserId(report.getReportedUserId())
                            .reportedUserName(resolveName(reported))
                            .reason(report.getReason())
                            .status(report.getStatus())
                            .adminNote(report.getAdminNote())
                            .createdAt(toEpochMillis(report.getCreatedAt()))
                            .handledAt(toEpochMillis(report.getHandledAt()))
                            .build();
                }).toList())
                .page(p).pageSize(size).total(result.getTotalElements()).build();
    }

    @Override
    @Transactional
    public void handleReport(Long id, AdminActionRequest request, LoginUser loginUser) {
        requireAdmin(loginUser);
        ActivityReport report = activityReportRepository.findById(id)
                .orElseThrow(() -> new BusinessException("举报不存在"));
        String status = request == null ? "" : request.getStatus();
        status = status == null ? "" : status.trim().toUpperCase();
        if (!Set.of("RESOLVED", "DISMISSED").contains(status)) {
            throw new BusinessException("举报处理状态无效");
        }
        String reason = requireReason(request);
        report.setStatus(status);
        report.setAdminNote(reason);
        report.setHandledBy(loginUser.userId());
        report.setHandledAt(LocalDateTime.now());
        activityReportRepository.save(report);
        notificationService.createNotification(
                report.getReporterUserId(),
                Notification.TYPE_REPORT_RESULT,
                "举报处理结果",
                ("RESOLVED".equals(status) ? "你提交的举报已核实处理。" : "你提交的举报经核查未成立。")
                        + "处理说明：" + reason,
                Notification.RELATED_ACTIVITY,
                report.getActivityId()
        );
        saveAudit(loginUser.userId(), "REPORT_" + status, "REPORT", id, reason);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserPageVO listUsers(
            String keyword,
            String status,
            Integer page,
            Integer pageSize,
            LoginUser loginUser
    ) {
        requireAdmin(loginUser);
        int p = normalizePage(page);
        int size = normalizePageSize(pageSize);
        String key = StringUtils.hasText(keyword) ? keyword.trim() : null;
        String normalizedStatus = status != null && Set.of("ACTIVE", "DISABLED").contains(status)
                ? status : null;
        Page<User> result = userRepository.findAdminUsers(
                key, normalizedStatus, PageRequest.of(p - 1, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        List<AdminUserItemVO> items = result.getContent().stream().map(user ->
                AdminUserItemVO.builder()
                        .id(user.getId())
                        .nickname(resolveName(user))
                        .avatarUrl(user.getAvatarUrl())
                        .status(user.getStatus())
                        .createdAt(toEpochMillis(user.getCreatedAt()))
                        .lastLoginAt(toEpochMillis(user.getLastLoginAt()))
                        .createdActivityCount(activityRepository.countByCreatorUserId(user.getId()))
                        .joinedActivityCount(activityRegistrationRepository.countByUserIdAndStatusIn(
                                user.getId(), JOINED_STATUSES))
                        .reportCount(activityReportRepository.countByReportedUserId(user.getId()))
                        .build()).toList();
        return AdminUserPageVO.builder()
                .list(items).page(p).pageSize(size).total(result.getTotalElements()).build();
    }

    @Override
    @Transactional
    public void updateUserStatus(Long id, AdminActionRequest request, LoginUser loginUser) {
        requireAdmin(loginUser);
        User user = userRepository.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        if (ROLE_ADMIN.equals(user.getRole())) throw new BusinessException("不能操作管理员账号");
        String status = request == null ? "" : request.getStatus();
        status = status == null ? "" : status.trim().toUpperCase();
        if (!Set.of("ACTIVE", "DISABLED").contains(status)) {
            throw new BusinessException("用户状态无效");
        }
        String reason = requireReason(request);
        user.setStatus(status);
        userRepository.save(user);
        saveAudit(loginUser.userId(), "USER_" + status, "USER", id, reason);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminTagVO> listTags(LoginUser loginUser) {
        requireAdmin(loginUser);
        return tagRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(this::toAdminTagVO)
                .toList();
    }

    @Override
    @Transactional
    public AdminTagVO createTag(AdminTagRequest request, LoginUser loginUser) {
        requireAdmin(loginUser);
        String name = requireTagName(request == null ? null : request.getName(), null);
        Tag saved = tagRepository.save(Tag.builder()
                .name(name)
                .enabled(request == null || request.getEnabled() == null || request.getEnabled())
                .sortOrder(request == null || request.getSortOrder() == null ? 0 : request.getSortOrder())
                .build());
        saveAudit(loginUser.userId(), "TAG_CREATE", "TAG", saved.getId(), name);
        return toAdminTagVO(saved);
    }

    @Override
    @Transactional
    public AdminTagVO updateTag(Long id, AdminTagRequest request, LoginUser loginUser) {
        requireAdmin(loginUser);
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new BusinessException("标签不存在"));
        String oldName = tag.getName();
        tag.setName(requireTagName(request == null ? null : request.getName(), id));
        if (request != null && request.getEnabled() != null) {
            tag.setEnabled(request.getEnabled());
        }
        if (request != null && request.getSortOrder() != null) {
            tag.setSortOrder(request.getSortOrder());
        }
        Tag saved = tagRepository.save(tag);
        if (!oldName.equals(saved.getName())) {
            syncActivityTagSnapshots();
        }
        saveAudit(loginUser.userId(), "TAG_UPDATE", "TAG", id,
                oldName + " -> " + saved.getName());
        return toAdminTagVO(saved);
    }

    @Override
    @Transactional
    public void deleteTag(Long id, LoginUser loginUser) {
        requireAdmin(loginUser);
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new BusinessException("标签不存在"));
        long usage = countTagUsage(id);
        if (usage > 0) {
            throw new BusinessException("该标签仍被 " + usage + " 个活动使用，请改为停用");
        }
        tagRepository.delete(tag);
        saveAudit(loginUser.userId(), "TAG_DELETE", "TAG", id, tag.getName());
    }

    @Override
    @Transactional
    public AdminActivityDetailVO updateActivityTags(
            Long id, AdminActivityTagsRequest request, LoginUser loginUser
    ) {
        requireAdmin(loginUser);
        Activity activity = activityRepository.findById(id)
                .orElseThrow(() -> new BusinessException("活动不存在"));
        List<Long> tagIds = request == null || request.getTagIds() == null
                ? List.of()
                : request.getTagIds().stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (tagIds.isEmpty() || tagIds.size() > 4) {
            throw new BusinessException("活动必须选择1至4个标签");
        }
        List<Tag> tags = tagRepository.findAllById(tagIds);
        if (tags.size() != tagIds.size()) {
            throw new BusinessException("包含不存在的标签");
        }
        Map<Long, Tag> tagMap = tags.stream().collect(Collectors.toMap(Tag::getId, Function.identity()));
        List<String> names = tagIds.stream().map(tagMap::get).map(Tag::getName).toList();
        activity.setTagId(tagIds.get(0));
        activity.setTagIdsJson(writeJson(tagIds));
        activity.setTagsJson(writeJson(names));
        activityRepository.save(activity);
        notifyAdminActivityAction(
                activity,
                "活动标签已由管理员调整",
                "「" + activity.getTitle() + "」的活动标签已由管理员调整。"
        );
        saveAudit(loginUser.userId(), "ACTIVITY_TAGS_UPDATE", "ACTIVITY", id,
                String.join("、", names));
        return buildActivityDetail(activity);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminFeedbackPageVO listFeedback(
            String status, Integer page, Integer pageSize, LoginUser loginUser
    ) {
        requireAdmin(loginUser);
        int p = normalizePage(page);
        int size = normalizePageSize(pageSize);
        String normalizedStatus = StringUtils.hasText(status) ? status.trim().toUpperCase() : null;
        Page<UserFeedback> result = normalizedStatus == null
                ? userFeedbackRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(p - 1, size))
                : userFeedbackRepository.findByStatusOrderByCreatedAtDesc(
                        normalizedStatus, PageRequest.of(p - 1, size));
        Map<Long, User> users = userRepository.findAllById(
                        result.getContent().stream().map(UserFeedback::getUserId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return AdminFeedbackPageVO.builder()
                .list(result.getContent().stream().map(feedback -> {
                    User user = users.get(feedback.getUserId());
                    return AdminFeedbackItemVO.builder()
                            .id(feedback.getId()).userId(feedback.getUserId())
                            .nickname(resolveName(user))
                            .avatarUrl(user == null ? null : user.getAvatarUrl())
                            .subject(feedback.getSubject()).content(feedback.getContent())
                            .status(feedback.getStatus()).adminNote(feedback.getAdminNote())
                            .createdAt(toEpochMillis(feedback.getCreatedAt()))
                            .handledAt(toEpochMillis(feedback.getHandledAt()))
                            .build();
                }).toList())
                .page(p).pageSize(size).total(result.getTotalElements()).build();
    }

    @Override
    @Transactional
    public void handleFeedback(Long id, AdminActionRequest request, LoginUser loginUser) {
        requireAdmin(loginUser);
        UserFeedback feedback = userFeedbackRepository.findById(id)
                .orElseThrow(() -> new BusinessException("意见不存在"));
        String status = request == null ? "" : request.getStatus();
        status = status == null ? "" : status.trim().toUpperCase();
        if (!Set.of("RESOLVED", "IGNORED").contains(status)) {
            throw new BusinessException("处理状态无效");
        }
        String note = requireReason(request);
        feedback.setStatus(status);
        feedback.setAdminNote(note);
        feedback.setHandledBy(loginUser.userId());
        feedback.setHandledAt(LocalDateTime.now());
        userFeedbackRepository.save(feedback);
        notificationService.createNotification(
                feedback.getUserId(),
                Notification.TYPE_FEEDBACK_RESULT,
                "意见处理结果",
                ("RESOLVED".equals(status) ? "你提交的意见已处理。" : "你提交的意见暂未采纳。")
                        + "处理说明：" + note,
                null,
                feedback.getId()
        );
        saveAudit(loginUser.userId(), "FEEDBACK_" + status, "FEEDBACK", id, note);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminReviewPageVO listReviews(
            String targetType,
            String status,
            String keyword,
            Integer page,
            Integer pageSize,
            LoginUser loginUser
    ) {
        requireAdmin(loginUser);
        int p = normalizePage(page);
        int size = normalizePageSize(pageSize);
        String normalizedType = StringUtils.hasText(targetType)
                && Set.of(ActivityReview.TARGET_ACTIVITY, ActivityReview.TARGET_MEMBER)
                .contains(targetType.trim().toLowerCase())
                ? targetType.trim().toLowerCase()
                : null;
        String normalizedStatus = StringUtils.hasText(status)
                && Set.of(ActivityReview.STATUS_NORMAL, ActivityReview.STATUS_EXCLUDED)
                .contains(status.trim().toUpperCase())
                ? status.trim().toUpperCase()
                : null;
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;

        Page<ActivityReview> result = activityReviewRepository.findAdminReviews(
                normalizedType,
                normalizedStatus,
                normalizedKeyword,
                PageRequest.of(p - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"))
        );
        List<ActivityReview> reviews = result.getContent();
        Set<Long> userIds = reviews.stream()
                .flatMap(review -> java.util.stream.Stream.of(
                        review.getReviewerUserId(),
                        ActivityReview.TARGET_MEMBER.equals(review.getTargetType())
                                ? review.getTargetId()
                                : null,
                        review.getHandledBy()
                ))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Activity> activities = activityRepository.findAllById(
                        reviews.stream().map(ActivityReview::getActivityId).collect(Collectors.toSet())
                ).stream()
                .collect(Collectors.toMap(Activity::getId, Function.identity()));

        List<AdminReviewItemVO> items = reviews.stream().map(review -> {
            Activity activity = activities.get(review.getActivityId());
            User reviewer = users.get(review.getReviewerUserId());
            User target = ActivityReview.TARGET_MEMBER.equals(review.getTargetType())
                    ? users.get(review.getTargetId())
                    : null;
            User handler = users.get(review.getHandledBy());
            return AdminReviewItemVO.builder()
                    .id(review.getId())
                    .targetType(review.getTargetType())
                    .activityId(review.getActivityId())
                    .activityTitle(activity == null ? "活动已删除" : activity.getTitle())
                    .reviewerUserId(review.getReviewerUserId())
                    .reviewerName(resolveName(reviewer))
                    .targetId(review.getTargetId())
                    .targetName(ActivityReview.TARGET_ACTIVITY.equals(review.getTargetType())
                            ? (activity == null ? "活动已删除" : activity.getTitle())
                            : resolveName(target))
                    .overallScore(review.getOverallScore().doubleValue())
                    .scores(parseReviewScores(review.getScoresJson()))
                    .status(review.getStatus())
                    .adminNote(review.getAdminNote())
                    .handledBy(review.getHandledBy())
                    .handledByName(handler == null ? null : resolveName(handler))
                    .createdAt(toEpochMillis(review.getCreatedAt()))
                    .updatedAt(toEpochMillis(review.getUpdatedAt()))
                    .handledAt(toEpochMillis(review.getHandledAt()))
                    .build();
        }).toList();

        return AdminReviewPageVO.builder()
                .list(items)
                .page(p)
                .pageSize(size)
                .total(result.getTotalElements())
                .build();
    }

    @Override
    @Transactional
    public void updateReviewStatus(Long id, AdminActionRequest request, LoginUser loginUser) {
        requireAdmin(loginUser);
        requireAdminPassword(request == null ? null : request.getPassword());
        String reason = requireReason(request);
        String status = request == null || request.getStatus() == null
                ? ""
                : request.getStatus().trim().toUpperCase();
        if (!Set.of(ActivityReview.STATUS_NORMAL, ActivityReview.STATUS_EXCLUDED).contains(status)) {
            throw new BusinessException("评价治理状态无效");
        }

        ActivityReview review = activityReviewRepository.findById(id)
                .orElseThrow(() -> new BusinessException("评价不存在"));
        Map<String, Object> before = reviewAuditState(review);
        review.setStatus(status);
        review.setAdminNote(reason);
        review.setHandledBy(loginUser.userId());
        review.setHandledAt(LocalDateTime.now());
        activityReviewRepository.save(review);
        notificationService.createNotification(
                review.getReviewerUserId(),
                Notification.TYPE_REVIEW_MODERATED,
                ActivityReview.STATUS_EXCLUDED.equals(status) ? "评价已被隐藏" : "评价已恢复",
                (ActivityReview.STATUS_EXCLUDED.equals(status)
                        ? "你提交的一条评价已被管理员隐藏。"
                        : "你提交的一条评价已由管理员恢复。")
                        + "处理说明：" + reason,
                Notification.RELATED_ACTIVITY,
                review.getActivityId()
        );

        saveAudit(
                loginUser.userId(),
                ActivityReview.STATUS_EXCLUDED.equals(status)
                        ? "REVIEW_EXCLUDE"
                        : "REVIEW_RESTORE",
                "REVIEW",
                id,
                reason,
                before,
                reviewAuditState(review)
        );
    }

    @Override
    @Transactional
    public void sendUserNotification(
            Long userId, AdminNotificationRequest request, LoginUser loginUser
    ) {
        requireAdmin(loginUser);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        if (ROLE_ADMIN.equals(user.getRole())) {
            throw new BusinessException("不能向管理员账号发送用户通知");
        }
        String title = request == null ? "" : request.getTitle();
        String content = request == null ? "" : request.getContent();
        title = title == null ? "" : title.trim();
        content = content == null ? "" : content.trim();
        if (!StringUtils.hasText(title)) throw new BusinessException("请填写通知标题");
        if (!StringUtils.hasText(content)) throw new BusinessException("请填写通知内容");
        if (title.length() > 100) throw new BusinessException("通知标题最多100字");
        if (content.length() > 500) throw new BusinessException("通知内容最多500字");
        notificationService.createNotification(
                userId, Notification.TYPE_ADMIN_MESSAGE, title, content, null, null);
        saveAudit(loginUser.userId(), "USER_NOTIFICATION_SEND", "USER", userId, title);
    }

    private void notifyAdminActivityAction(Activity activity, String title, String content) {
        Set<Long> userIds = new java.util.LinkedHashSet<>();
        if (activity.getCreatorUserId() != null) {
            userIds.add(activity.getCreatorUserId());
        }
        activityRegistrationRepository.findByActivityIdAndStatusIn(activity.getId(), JOINED_STATUSES)
                .stream()
                .map(ActivityRegistration::getUserId)
                .forEach(userIds::add);
        userIds.forEach(userId -> notificationService.createNotification(
                userId,
                Notification.TYPE_ADMIN_ACTIVITY_ACTION,
                title,
                content,
                Notification.RELATED_ACTIVITY,
                activity.getId()
        ));
    }

    private String adminActivityActionLabel(String action) {
        return switch (action) {
            case "hide" -> "隐藏活动";
            case "restore" -> "恢复活动";
            case "force-end" -> "结束活动";
            default -> "处理活动";
        };
    }

    private AdminActivityDetailVO buildActivityDetail(Activity activity) {
        List<ActivityRegistration> registrations =
                activityRegistrationRepository.findByActivityIdOrderByCreatedAtAsc(activity.getId());
        Set<Long> userIds = registrations.stream().map(ActivityRegistration::getUserId)
                .collect(Collectors.toSet());
        Map<Long, User> users = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        int joined = (int) registrations.stream().filter(r -> JOINED_STATUSES.contains(r.getStatus())).count();
        int pending = (int) registrations.stream()
                .filter(r -> ActivityRegistration.STATUS_PENDING.equals(r.getStatus())).count();
        AdminAuditLog latestAudit = adminAuditLogRepository
                .findFirstByTargetTypeAndTargetIdOrderByCreatedAtDesc("ACTIVITY", activity.getId())
                .orElse(null);
        List<AdminAuditLog> auditLogs = adminAuditLogRepository
                .findByTargetTypeAndTargetIdOrderByCreatedAtDesc("ACTIVITY", activity.getId());
        Set<Long> adminIds = auditLogs.stream()
                .map(AdminAuditLog::getAdminUserId)
                .collect(Collectors.toSet());
        Map<Long, User> admins = userRepository.findAllById(adminIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return AdminActivityDetailVO.builder()
                .id(activity.getId()).title(activity.getTitle()).content(activity.getContent())
                .creatorUserId(activity.getCreatorUserId()).authorName(activity.getAuthorName())
                .avatarUrl(activity.getAvatarUrl()).status(activity.getStatus())
                .timeStatus(resolveStatus(activity, LocalDateTime.now()))
                .recruitCount(activity.getRecruitCount()).joinedCount(joined).pendingCount(pending)
                .startAt(toEpochMillis(activity.getStartAt())).endAt(toEpochMillis(activity.getEndAt()))
                .locationText(activity.getLocationText())
                .tagIds(parseLongList(activity.getTagIdsJson()))
                .tags(parseList(activity.getTagsJson()))
                .imageUrls(parseList(activity.getImageUrlsJson()))
                .reportCount(activityReportRepository.findByActivityIdIn(List.of(activity.getId())).stream().count())
                .governanceAction(latestAudit == null ? null : latestAudit.getActionType())
                .governanceReason(latestAudit == null ? null : latestAudit.getReason())
                .governedAt(latestAudit == null ? null : toEpochMillis(latestAudit.getCreatedAt()))
                .auditLogs(auditLogs.stream().map(audit -> AdminAuditLogVO.builder()
                        .id(audit.getId())
                        .adminUserId(audit.getAdminUserId())
                        .adminName(resolveName(admins.get(audit.getAdminUserId())))
                        .actionType(audit.getActionType())
                        .reason(audit.getReason())
                        .beforeState(parseObjectMap(audit.getBeforeJson()))
                        .afterState(parseObjectMap(audit.getAfterJson()))
                        .createdAt(toEpochMillis(audit.getCreatedAt()))
                        .build()).toList())
                .participants(registrations.stream().map(registration -> {
                    User user = users.get(registration.getUserId());
                    return AdminParticipantVO.builder()
                            .registrationId(registration.getId()).userId(registration.getUserId())
                            .nickname(resolveName(user)).avatarUrl(user == null ? null : user.getAvatarUrl())
                            .status(registration.getStatus()).applicationText(registration.getApplicationText())
                            .appliedAt(toEpochMillis(registration.getCreatedAt())).build();
                }).toList()).build();
    }

    private AdminTagVO toAdminTagVO(Tag tag) {
        return AdminTagVO.builder()
                .id(tag.getId())
                .name(tag.getName())
                .enabled(tag.getEnabled())
                .sortOrder(tag.getSortOrder())
                .activityCount(countTagUsage(tag.getId()))
                .createdAt(toEpochMillis(tag.getCreatedAt()))
                .updatedAt(toEpochMillis(tag.getUpdatedAt()))
                .build();
    }

    private long countTagUsage(Long tagId) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM activities
                WHERE tag_id = ?
                   OR (tag_ids_json IS NOT NULL
                       AND JSON_VALID(tag_ids_json)
                       AND JSON_CONTAINS(tag_ids_json, CAST(? AS JSON)))
                """, Long.class, tagId, String.valueOf(tagId));
        return count == null ? 0L : count;
    }

    private String requireTagName(String rawName, Long currentId) {
        String name = rawName == null ? "" : rawName.trim();
        if (!StringUtils.hasText(name)) {
            throw new BusinessException("请填写标签名称");
        }
        if (name.length() > 40) {
            throw new BusinessException("标签名称最多40个字符");
        }
        tagRepository.findByNameIgnoreCase(name).ifPresent(existing -> {
            if (currentId == null || !currentId.equals(existing.getId())) {
                throw new BusinessException("标签名称已存在");
            }
        });
        return name;
    }

    private void syncActivityTagSnapshots() {
        Map<Long, String> names = tagRepository.findAll().stream()
                .collect(Collectors.toMap(Tag::getId, Tag::getName));
        for (Activity activity : activityRepository.findAll()) {
            List<Long> ids = parseLongList(activity.getTagIdsJson());
            if (ids.isEmpty() && activity.getTagId() != null) {
                ids = List.of(activity.getTagId());
            }
            List<String> activityTagNames = ids.stream()
                    .map(names::get)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            activity.setTagsJson(writeJson(activityTagNames));
        }
        activityRepository.flush();
    }

    private List<String> parseList(String json) {
        if (!StringUtils.hasText(json)) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private List<Long> parseLongList(String json) {
        if (!StringUtils.hasText(json)) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {});
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new BusinessException("标签数据保存失败");
        }
    }

    private String resolveName(User user) {
        return user == null || !StringUtils.hasText(user.getNickname()) ? "未知用户" : user.getNickname();
    }

    private String requireReason(AdminActionRequest request) {
        String reason = request == null ? "" : request.getReason();
        reason = reason == null ? "" : reason.trim();
        if (!StringUtils.hasText(reason)) throw new BusinessException("请填写处理原因");
        if (reason.length() > 500) throw new BusinessException("处理原因最多 500 字");
        return reason;
    }

    private int normalizePage(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private int normalizePageSize(Integer pageSize) {
        return pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 100);
    }

    private void saveAudit(Long adminId, String action, String targetType, Long targetId, String reason) {
        saveAudit(adminId, action, targetType, targetId, reason, null, null);
    }

    private void saveAudit(
            Long adminId,
            String action,
            String targetType,
            Long targetId,
            String reason,
            Map<String, Object> before,
            Map<String, Object> after
    ) {
        adminAuditLogRepository.save(AdminAuditLog.builder()
                .adminUserId(adminId).actionType(action).targetType(targetType)
                .targetId(targetId).reason(reason)
                .beforeJson(toJsonOrNull(before))
                .afterJson(toJsonOrNull(after))
                .build());
    }

    private void requireAdmin(LoginUser loginUser) {
        if (loginUser == null || !ROLE_ADMIN.equals(loginUser.role())) {
            throw new BusinessException("无权访问管理后台");
        }
    }

    private void requireAdminPassword(String password) {
        if (!StringUtils.hasText(password)
                || !adminProperties.getPassword().equals(password)) {
            throw new BusinessException("管理员密码错误");
        }
    }

    private Map<String, Object> activityAuditState(Activity activity) {
        Map<String, Object> state = new HashMap<>();
        state.put("status", activity.getStatus());
        state.put("startAt", toEpochMillis(activity.getStartAt()));
        state.put("endAt", toEpochMillis(activity.getEndAt()));
        state.put("locationText", activity.getLocationText());
        return state;
    }

    private Map<String, Object> reviewAuditState(ActivityReview review) {
        Map<String, Object> state = new HashMap<>();
        state.put("status", review.getStatus());
        state.put("overallScore", review.getOverallScore());
        state.put("adminNote", review.getAdminNote());
        return state;
    }

    private List<ReviewScoreRequest> parseReviewScores(String value) {
        if (!StringUtils.hasText(value)) return List.of();
        try {
            return objectMapper.readValue(
                    value,
                    new TypeReference<List<ReviewScoreRequest>>() {}
            );
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String buildMapImageUrl(String address) {
        if (!StringUtils.hasText(googleMapsProperties.getApiKey())) {
            return null;
        }
        String imageUrl = UriComponentsBuilder
                .fromUriString("https://maps.googleapis.com/maps/api/staticmap")
                .queryParam("center", address)
                .queryParam("zoom", 15)
                .queryParam("size", "800x360")
                .queryParam("scale", 2)
                .queryParam("maptype", "roadmap")
                .queryParam("markers", "color:red|" + address)
                .queryParam("key", googleMapsProperties.getApiKey())
                .toUriString();
        return "/api/map/image-proxy?url="
                + UriUtils.encodeQueryParam(imageUrl, StandardCharsets.UTF_8);
    }

    private String toJsonOrNull(Map<String, Object> value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new BusinessException("记录管理操作失败");
        }
    }

    private Map<String, Object> parseObjectMap(String value) {
        if (!StringUtils.hasText(value)) return Map.of();
        try {
            return objectMapper.readValue(value, new TypeReference<Map<String, Object>>() {});
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private String resolveStatus(Activity activity, LocalDateTime now) {
        if ("HIDDEN".equals(activity.getStatus())) {
            return "hidden";
        }
        if ("CANCELLED".equals(activity.getStatus())) {
            return "cancelled";
        }
        if (!activity.getStartAt().isAfter(now) && activity.getEndAt().isAfter(now)) {
            return "ongoing";
        }
        if (activity.getEndAt().isBefore(now) || activity.getEndAt().isEqual(now)) {
            return "ended";
        }
        return "upcoming";
    }

    private Long toEpochMillis(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private LocalDateTime fromEpochMillis(Long value) {
        if (value == null || value <= 0) return null;
        return LocalDateTime.ofInstant(
                Instant.ofEpochMilli(value),
                ZoneId.systemDefault()
        );
    }

    private long scalar(String sql, Object... arguments) {
        Long result = jdbcTemplate.queryForObject(sql, Long.class, arguments);
        return result == null ? 0L : result;
    }

    private int addMetric(Sheet sheet, int rowIndex, String label, Object value) {
        addRow(sheet, rowIndex, null, label, value);
        return rowIndex + 1;
    }

    private void addRow(Sheet sheet, int rowIndex, CellStyle style, Object... values) {
        Row row = sheet.createRow(rowIndex);
        for (int column = 0; column < values.length; column++) {
            Cell cell = row.createCell(column);
            Object value = values[column];
            if (value instanceof Number number) {
                cell.setCellValue(number.doubleValue());
            } else {
                cell.setCellValue(value == null ? "" : String.valueOf(value));
            }
            if (style != null) {
                cell.setCellStyle(style);
            }
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
