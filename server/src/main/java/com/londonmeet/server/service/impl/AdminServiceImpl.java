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
import com.londonmeet.pojo.entity.AdminAuditLog;
import com.londonmeet.pojo.entity.Activity;
import com.londonmeet.pojo.entity.ActivityRegistration;
import com.londonmeet.pojo.entity.ActivityReport;
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
import com.londonmeet.pojo.vo.AdminActivityAnalyticsVO;
import com.londonmeet.pojo.vo.AdminUserAnalyticsVO;
import com.londonmeet.server.config.AdminProperties;
import com.londonmeet.server.config.GoogleMapsProperties;
import com.londonmeet.server.config.UploadProperties;
import com.londonmeet.server.repository.ActivityRegistrationRepository;
import com.londonmeet.server.repository.AdminAuditLogRepository;
import com.londonmeet.server.repository.ActivityReportRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
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
    private final AdminAuditLogRepository adminAuditLogRepository;
    private final TagRepository tagRepository;
    private final UserFeedbackRepository userFeedbackRepository;
    private final NotificationService notificationService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public AdminActivityAnalyticsVO getActivityAnalytics(
            String startDate,
            String endDate,
            Integer page,
            Integer pageSize,
            LoginUser loginUser
    ) {
        requireAdmin(loginUser);
        LocalDate[] range = analyticsRange(startDate, endDate);
        LocalDateTime start = range[0].atStartOfDay();
        LocalDateTime end = range[1].plusDays(1).atStartOfDay();
        int p = normalizePage(page);
        int size = normalizePageSize(pageSize);
        long total = scalar("""
                SELECT COUNT(*) FROM activities
                WHERE created_at < ? AND end_at >= ?
                """, end, start);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT a.id, a.title, a.author_name, a.tags_json,
                       (SELECT COUNT(DISTINCT e.user_id) FROM activity_analytics_events e
                         WHERE e.activity_id=a.id AND e.event_type='EXPOSURE'
                           AND e.created_at>=? AND e.created_at<?) exposure_users,
                       (SELECT COUNT(DISTINCT e.user_id) FROM activity_analytics_events e
                         WHERE e.activity_id=a.id AND e.event_type='DETAIL_VIEW'
                           AND e.created_at>=? AND e.created_at<?) detail_users,
                       (SELECT COUNT(DISTINCT f.user_id) FROM activity_favorites f
                         WHERE f.activity_id=a.id AND f.created_at>=? AND f.created_at<?) favorite_users,
                       (SELECT COUNT(*) FROM activity_registrations r
                         WHERE r.activity_id=a.id AND r.created_at>=? AND r.created_at<?) application_count,
                       (SELECT COUNT(*) FROM activity_registrations r
                         WHERE r.activity_id=a.id
                           AND r.approved_at>=? AND r.approved_at<?) approved_count,
                       (SELECT COUNT(*) FROM activity_registrations r
                         WHERE r.activity_id=a.id AND r.joined_group_at>=? AND r.joined_group_at<?) joined_group_count,
                       (SELECT COUNT(DISTINCT e.user_id) FROM activity_analytics_events e
                         WHERE e.activity_id=a.id AND e.event_type='QR_OPEN'
                           AND e.created_at>=? AND e.created_at<?) group_qr_users,
                       (SELECT COUNT(*) FROM activity_registrations r
                         WHERE r.activity_id=a.id AND r.cancelled_at>=? AND r.cancelled_at<?) cancelled_count,
                       (SELECT COUNT(*) FROM activity_reports rp
                         WHERE rp.activity_id=a.id AND rp.created_at>=? AND rp.created_at<?) report_count
                FROM activities a
                WHERE a.created_at < ? AND a.end_at >= ?
                ORDER BY a.created_at DESC
                LIMIT ? OFFSET ?
                """,
                start, end, start, end, start, end, start, end, start, end,
                start, end, start, end, start, end, start, end,
                end, start, size, (p - 1) * size);
        List<AdminActivityAnalyticsVO.ActivityItem> items = rows.stream().map(row ->
                AdminActivityAnalyticsVO.ActivityItem.builder()
                        .activityId(number(row, "id"))
                        .title(text(row.get("title")))
                        .creatorName(text(row.get("author_name")))
                        .tags(formatTags(row.get("tags_json")))
                        .exposureUsers(number(row, "exposure_users"))
                        .detailUsers(number(row, "detail_users"))
                        .favoriteUsers(number(row, "favorite_users"))
                        .applicationCount(number(row, "application_count"))
                        .approvedCount(number(row, "approved_count"))
                        .joinedGroupCount(number(row, "joined_group_count"))
                        .groupQrUsers(number(row, "group_qr_users"))
                        .cancelledCount(number(row, "cancelled_count"))
                        .reportCount(number(row, "report_count"))
                        .exposureToDetailRate(rate(number(row, "detail_users"), number(row, "exposure_users")))
                        .detailToApplyRate(rate(number(row, "application_count"), number(row, "detail_users")))
                        .approvalRate(rate(number(row, "approved_count"), number(row, "application_count")))
                        .groupJoinRate(rate(number(row, "joined_group_count"), number(row, "approved_count")))
                        .groupQrViewRate(rate(number(row, "group_qr_users"), number(row, "approved_count")))
                        .build()).toList();
        return AdminActivityAnalyticsVO.builder()
                .startDate(range[0].toString()).endDate(range[1].toString())
                .total(total).list(items).build();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserAnalyticsVO getUserAnalytics(
            String startDate, String endDate, LoginUser loginUser) {
        requireAdmin(loginUser);
        LocalDate[] range = analyticsRange(startDate, endDate);
        LocalDateTime start = range[0].atStartOfDay();
        LocalDateTime end = range[1].plusDays(1).atStartOfDay();
        long newUsers = scalar("SELECT COUNT(*) FROM users WHERE role='USER' AND created_at>=? AND created_at<?", start, end);
        long activeUsers = scalar("""
                SELECT COUNT(DISTINCT user_id) FROM (
                    SELECT id user_id FROM users WHERE role='USER' AND last_login_at>=? AND last_login_at<?
                    UNION SELECT user_id FROM activity_analytics_events WHERE created_at>=? AND created_at<?
                    UNION SELECT creator_user_id FROM activities WHERE created_at>=? AND created_at<?
                    UNION SELECT user_id FROM activity_registrations WHERE created_at>=? AND created_at<?
                    UNION SELECT user_id FROM activity_favorites WHERE created_at>=? AND created_at<?
                    UNION SELECT reporter_user_id FROM activity_reports WHERE created_at>=? AND created_at<?
                    UNION SELECT user_id FROM user_feedback WHERE created_at>=? AND created_at<?
                ) active
                """, start, end, start, end, start, end, start, end, start, end, start, end, start, end);
        long creators = scalar("SELECT COUNT(DISTINCT creator_user_id) FROM activities WHERE created_at>=? AND created_at<?", start, end);
        long applicants = scalar("SELECT COUNT(DISTINCT user_id) FROM activity_registrations WHERE created_at>=? AND created_at<?", start, end);
        long both = scalar("""
                SELECT COUNT(*) FROM (
                    SELECT DISTINCT creator_user_id user_id FROM activities WHERE created_at>=? AND created_at<?
                ) c JOIN (
                    SELECT DISTINCT user_id FROM activity_registrations WHERE created_at>=? AND created_at<?
                ) r ON r.user_id=c.user_id
                """, start, end, start, end);
        long totalUsers = scalar("SELECT COUNT(*) FROM users WHERE role='USER'");
        long applications = scalar("SELECT COUNT(*) FROM activity_registrations WHERE created_at>=? AND created_at<?", start, end);
        List<AdminUserAnalyticsVO.DailyItem> daily = new ArrayList<>();
        for (LocalDate date = range[0]; !date.isAfter(range[1]); date = date.plusDays(1)) {
            LocalDateTime dayStart = date.atStartOfDay();
            LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
            daily.add(AdminUserAnalyticsVO.DailyItem.builder()
                    .date(date.toString())
                    .newUsers(scalar("SELECT COUNT(*) FROM users WHERE role='USER' AND created_at>=? AND created_at<?", dayStart, dayEnd))
                    .activeUsers(activeUsersBetween(dayStart, dayEnd))
                    .activities(scalar("SELECT COUNT(*) FROM activities WHERE created_at>=? AND created_at<?", dayStart, dayEnd))
                    .applications(scalar("SELECT COUNT(*) FROM activity_registrations WHERE created_at>=? AND created_at<?", dayStart, dayEnd))
                    .approved(scalar("SELECT COUNT(*) FROM activity_registrations WHERE approved_at>=? AND approved_at<?", dayStart, dayEnd))
                    .build());
        }
        return AdminUserAnalyticsVO.builder()
                .startDate(range[0].toString()).endDate(range[1].toString())
                .newUserCount(newUsers).activeUserCount(activeUsers)
                .creatorUserCount(creators).applicantUserCount(applicants)
                .creatorAndApplicantCount(both)
                .inactiveUserCount(Math.max(0, totalUsers - activeUsers))
                .applicationsPerApplicant(applicants == 0 ? 0D
                        : Math.round(applications * 100D / applicants) / 100D)
                .dailyTrend(daily).build();
    }

    @Override
    @Transactional
    public AdminLoginVO login(AdminLoginRequest request) {
        String username = request == null ? "" : request.getUsername();
        String password = request == null ? "" : request.getPassword();
        username = username == null ? "" : username.trim();
        final String adminUsername = username;

        if (!adminProperties.getUsername().equals(adminUsername)
                || !adminProperties.getPassword().equals(password)) {
            throw new BusinessException("绠＄悊鍛樿处鍙锋垨瀵嗙爜閿欒");
        }

        String openid = "admin:" + adminUsername;
        User admin = userRepository.findByOpenid(openid)
                .orElseGet(() -> {
                    String userId = generatePublicUserId();
                    return User.builder()
                            .publicId(userId)
                            .displayId(userId)
                            .openid(openid)
                            .nickname(adminUsername)
                            .avatarUrl(uploadProperties.getDefaultAvatarUrl())
                            .coverUrl(uploadProperties.getDefaultCoverUrl())
                            .role(ROLE_ADMIN)
                            .status(STATUS_ACTIVE)
                            .build();
                });
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
    public AdminDashboardVO getDashboard(Integer days, LoginUser loginUser) {
        requireAdmin(loginUser);
        LocalDateTime now = LocalDateTime.now();
        int periodDays = days != null && days == 7 ? 7 : 30;
        LocalDate firstDate = LocalDate.now().minusDays(periodDays - 1L);
        LocalDateTime periodStart = firstDate.atStartOfDay();
        List<AdminDashboardVO.DailyCount> trend = new ArrayList<>();
        List<AdminDashboardVO.DailyOverview> dailyTrend = new ArrayList<>();

        for (int offset = periodDays - 1; offset >= 0; offset--) {
            LocalDate date = LocalDate.now().minusDays(offset);
            LocalDateTime start = date.atStartOfDay();
            LocalDateTime end = date.plusDays(1).atStartOfDay();
            trend.add(AdminDashboardVO.DailyCount.builder()
                    .date(date.toString())
                    .count(activityRepository.countByCreatedAtBetween(start, end))
                    .build());
            dailyTrend.add(AdminDashboardVO.DailyOverview.builder()
                    .date(date.toString())
                    .newUsers(scalar("SELECT COUNT(*) FROM users WHERE role='USER' AND created_at>=? AND created_at<?", start, end))
                    .activeUsers(activeUsersBetween(start, end))
                    .activities(scalar("SELECT COUNT(*) FROM activities WHERE created_at>=? AND created_at<?", start, end))
                    .registrations(scalar("SELECT COUNT(*) FROM activity_registrations WHERE created_at>=? AND created_at<?", start, end))
                    .approved(scalar("SELECT COUNT(*) FROM activity_registrations WHERE approved_at>=? AND approved_at<?", start, end))
                    .build());
        }

        return AdminDashboardVO.builder()
                .userCount(userRepository.count())
                .activityCount(activityRepository.count())
                .registrationCount(activityRegistrationRepository.count())
                .pendingReportCount(activityReportRepository.countByStatus("PENDING"))
                .upcomingActivityCount(scalar("SELECT COUNT(*) FROM activities WHERE status='PUBLISHED' AND start_at > ?", now))
                .ongoingActivityCount(scalar("SELECT COUNT(*) FROM activities WHERE status='PUBLISHED' AND start_at <= ? AND end_at > ?", now, now))
                .endedActivityCount(scalar("SELECT COUNT(*) FROM activities WHERE status='PUBLISHED' AND end_at <= ?", now))
                .hiddenActivityCount(scalar("SELECT COUNT(*) FROM activities WHERE status='HIDDEN'"))
                .cancelledActivityCount(scalar("SELECT COUNT(*) FROM activities WHERE status='CANCELLED'"))
                .newUserCount(scalar("SELECT COUNT(*) FROM users WHERE role='USER' AND created_at>=?", periodStart))
                .activeUserCount(activeUsersBetween(periodStart, now.plusSeconds(1)))
                .newActivityCount(scalar("SELECT COUNT(*) FROM activities WHERE created_at>=?", periodStart))
                .periodRegistrationCount(scalar("SELECT COUNT(*) FROM activity_registrations WHERE created_at>=?", periodStart))
                .approvedRegistrationCount(scalar("SELECT COUNT(*) FROM activity_registrations WHERE approved_at>=?", periodStart))
                .cancelledRegistrationCount(scalar("SELECT COUNT(*) FROM activity_registrations WHERE status='cancelled' AND cancelled_at>=?", periodStart))
                .pendingAppealCount(scalar("SELECT COUNT(*) FROM user_feedback WHERE type='ACCOUNT_APPEAL' AND status='PENDING'"))
                .activityTrend(trend)
                .dailyTrend(dailyTrend)
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
                .retentionDescription("Clean images, QR codes, registrations and reports 30 days after activity end; keep activity skeleton.")
                .exportDescription("Reports are generated on demand as Excel files for the selected date range.")
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportRecentReport(String startDate, String endDate, LoginUser loginUser) {
        requireAdmin(loginUser);
        LocalDate[] range = analyticsRange(startDate, endDate);
        LocalDateTime start = range[0].atStartOfDay();
        LocalDateTime end = range[1].plusDays(1).atStartOfDay();
        DateTimeFormatter dateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Sheet summary = workbook.createSheet("Summary");
            addRow(summary, 0, headerStyle, "Metric", "Value");
            int row = 1;
            row = addMetric(summary, row, "Start time", start.format(dateTime));
            row = addMetric(summary, row, "End time", end.format(dateTime));
            row = addMetric(summary, row, "Total users",
                    scalar("SELECT COUNT(*) FROM users WHERE role <> 'ADMIN'"));
            row = addMetric(summary, row, "New users",
                    scalar("SELECT COUNT(*) FROM users WHERE role <> 'ADMIN' AND created_at >= ?", start));
            row = addMetric(summary, row, "Total activities",
                    scalar("SELECT COUNT(*) FROM activities"));
            row = addMetric(summary, row, "New activities",
                    scalar("SELECT COUNT(*) FROM activities WHERE created_at >= ?", start));
            row = addMetric(summary, row, "Ended activities",
                    scalar("SELECT COUNT(*) FROM activities WHERE end_at >= ? AND end_at <= ?", start, end));
            row = addMetric(summary, row, "Force-ended activities",
                    scalar("SELECT COUNT(*) FROM admin_audit_logs WHERE action_type = 'ACTIVITY_FORCE_END' AND created_at >= ?", start));
            row = addMetric(summary, row, "Registrations",
                    scalar("SELECT COUNT(*) FROM activity_registrations WHERE created_at >= ?", start));
            row = addMetric(summary, row, "Approved registrations",
                    scalar("SELECT COUNT(*) FROM activity_registrations WHERE approved_at >= ? AND approved_at < ?", start, end));
            row = addMetric(summary, row, "Rejected registrations",
                    scalar("SELECT COUNT(*) FROM activity_registrations WHERE created_at >= ? AND status = 'rejected'", start));
            row = addMetric(summary, row, "Favorites",
                    scalar("SELECT COUNT(*) FROM activity_favorites WHERE created_at >= ?", start));
            row = addMetric(summary, row, "Reports",
                    scalar("SELECT COUNT(*) FROM activity_reports WHERE created_at >= ?", start));
            row = addMetric(summary, row, "Handled reports",
                    scalar("SELECT COUNT(*) FROM activity_reports WHERE created_at >= ? AND status IN ('RESOLVED','DISMISSED')", start));
            row = addMetric(summary, row, "Pending reports",
                    scalar("SELECT COUNT(*) FROM activity_reports WHERE created_at >= ? AND status = 'PENDING'", start));
            summary.setColumnWidth(0, 32 * 256);
            summary.setColumnWidth(1, 24 * 256);

            Sheet details = workbook.createSheet("Activities");
            String[] headers = {
                    "Activity ID", "Title", "Creator", "Created at", "Start at", "End at",
                    "Status", "Capacity", "Registrations", "Approved", "Pending", "Favorites", "Reports",
                    "Exposure users", "Detail users", "QR users",
                    "Location", "Latest admin action", "Action reason"
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
                           (SELECT COUNT(DISTINCT e.user_id) FROM activity_analytics_events e
                               WHERE e.activity_id=a.id AND e.event_type='EXPOSURE'
                                 AND e.created_at>=? AND e.created_at<?) exposure_users,
                           (SELECT COUNT(DISTINCT e.user_id) FROM activity_analytics_events e
                               WHERE e.activity_id=a.id AND e.event_type='DETAIL_VIEW'
                                 AND e.created_at>=? AND e.created_at<?) detail_users,
                           (SELECT COUNT(DISTINCT e.user_id) FROM activity_analytics_events e
                               WHERE e.activity_id=a.id AND e.event_type='QR_OPEN'
                                 AND e.created_at>=? AND e.created_at<?) qr_users,
                           (SELECT l.action_type FROM admin_audit_logs l
                               WHERE l.target_type = 'ACTIVITY' AND l.target_id = a.id
                               ORDER BY l.created_at DESC LIMIT 1) governance_action,
                           (SELECT l.reason FROM admin_audit_logs l
                               WHERE l.target_type = 'ACTIVITY' AND l.target_id = a.id
                               ORDER BY l.created_at DESC LIMIT 1) governance_reason
                    FROM activities a
                    WHERE a.created_at >= ? OR a.end_at >= ?
                    ORDER BY a.created_at DESC
                    """, start, end, start, end, start, end, start, start);
            int detailRow = 1;
            for (Map<String, Object> activity : activities) {
                addRow(details, detailRow++, null,
                        activity.get("id"), activity.get("title"), activity.get("author_name"),
                        text(activity.get("created_at")), text(activity.get("start_at")), text(activity.get("end_at")),
                        activity.get("status"), activity.get("recruit_count"), activity.get("registration_count"),
                        activity.get("joined_count"), activity.get("pending_count"), activity.get("favorite_count"),
                        activity.get("report_count"), activity.get("exposure_users"), activity.get("detail_users"),
                        activity.get("qr_users"),
                        activity.get("location_text"),
                        activity.get("governance_action"), activity.get("governance_reason"));
            }
            for (int column = 0; column < headers.length; column++) {
                details.setColumnWidth(column, Math.min(column == 1 || column == 13 || column == 15 ? 32 : 18, 40) * 256);
            }
            details.createFreezePane(0, 1);
            details.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(0, detailRow - 1), 0, headers.length - 1));

            Sheet daily = workbook.createSheet("Daily Trend");
            addRow(daily, 0, headerStyle,
                    "Date", "New users", "Active users", "New activities", "Registrations", "Approved", "Cancelled");
            int dailyRow = 1;
            for (LocalDate date = range[0]; !date.isAfter(range[1]); date = date.plusDays(1)) {
                LocalDateTime dayStart = date.atStartOfDay();
                LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
                addRow(daily, dailyRow++, null,
                        date,
                        scalar("SELECT COUNT(*) FROM users WHERE role='USER' AND created_at>=? AND created_at<?", dayStart, dayEnd),
                        activeUsersBetween(dayStart, dayEnd),
                        scalar("SELECT COUNT(*) FROM activities WHERE created_at>=? AND created_at<?", dayStart, dayEnd),
                        scalar("SELECT COUNT(*) FROM activity_registrations WHERE created_at>=? AND created_at<?", dayStart, dayEnd),
                        scalar("SELECT COUNT(*) FROM activity_registrations WHERE approved_at>=? AND approved_at<?", dayStart, dayEnd),
                        scalar("SELECT COUNT(*) FROM activity_registrations WHERE status='cancelled' AND cancelled_at>=? AND cancelled_at<?", dayStart, dayEnd));
            }
            for (int column = 0; column < 7; column++) daily.setColumnWidth(column, 18 * 256);
            daily.createFreezePane(0, 1);

            Sheet users = workbook.createSheet("User Behavior");
            addRow(users, 0, headerStyle, "Metric", "Value");
            int userRow = 1;
            userRow = addMetric(users, userRow, "New users",
                    scalar("SELECT COUNT(*) FROM users WHERE role='USER' AND created_at>=? AND created_at<?", start, end));
            userRow = addMetric(users, userRow, "Active users",
                    activeUsersBetween(start, end));
            userRow = addMetric(users, userRow, "Activity creators",
                    scalar("SELECT COUNT(DISTINCT creator_user_id) FROM activities WHERE created_at>=? AND created_at<?", start, end));
            userRow = addMetric(users, userRow, "Registration users",
                    scalar("SELECT COUNT(DISTINCT user_id) FROM activity_registrations WHERE created_at>=? AND created_at<?", start, end));
            userRow = addMetric(users, userRow, "Favorite users",
                    scalar("SELECT COUNT(DISTINCT user_id) FROM activity_favorites WHERE created_at>=? AND created_at<?", start, end));
            userRow = addMetric(users, userRow, "Detail view users",
                    scalar("SELECT COUNT(DISTINCT user_id) FROM activity_analytics_events WHERE event_type='DETAIL_VIEW' AND created_at>=? AND created_at<?", start, end));
            addMetric(users, userRow, "QR open users",
                    scalar("SELECT COUNT(DISTINCT user_id) FROM activity_analytics_events WHERE event_type='QR_OPEN' AND created_at>=? AND created_at<?", start, end));
            users.setColumnWidth(0, 30 * 256);
            users.setColumnWidth(1, 20 * 256);

            Sheet governance = workbook.createSheet("Governance");
            addRow(governance, 0, headerStyle, "Metric", "Value");
            int governanceRow = 1;
            addMetric(governance, governanceRow, "Disabled accounts",

                    scalar("SELECT COUNT(*) FROM admin_audit_logs WHERE action_type='USER_DISABLED' AND created_at>=? AND created_at<?", start, end));
            governance.setColumnWidth(0, 30 * 256);
            governance.setColumnWidth(1, 20 * 256);

            workbook.write(output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new BusinessException("Failed to generate Excel report");
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
                && Set.of("upcoming", "ongoing", "ended", "hidden", "cancelled").contains(status)
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
                .orElseThrow(() -> new BusinessException("Activity not found"));
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
            throw new BusinessException("Please fill activity update content");
        }
        requireAdminPassword(request.getPassword());

        String reason = request.getReason() == null ? "" : request.getReason().trim();
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException("Please fill update reason");
        }
        if (reason.length() > 500) {
            throw new BusinessException("Update reason is too long");
        }

        String status = request.getStatus() == null ? "" : request.getStatus().trim().toUpperCase();
        if (!Set.of("PUBLISHED", "HIDDEN", "CANCELLED").contains(status)) {
            throw new BusinessException("Invalid activity status");
        }

        LocalDateTime startAt = fromEpochMillis(request.getStartAt());
        LocalDateTime endAt = fromEpochMillis(request.getEndAt());
        if (startAt == null || endAt == null) {
            throw new BusinessException("璇峰～鍐欐椿鍔ㄥ紑濮嬪拰缁撴潫鏃堕棿");
        }
        if (!endAt.isAfter(startAt)) {
            throw new BusinessException("Activity end time must be after start time");
        }

        String locationText = request.getLocationText() == null
                ? ""
                : request.getLocationText().trim();
        if (!StringUtils.hasText(locationText)) {
            throw new BusinessException("请填写活动地址");
        }
        if (locationText.length() > 500) {
            throw new BusinessException("Activity location is too long");
        }

        Activity activity = activityRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Activity not found"));
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
        Map<String, Object> after = activityAuditState(activity);
        notifyAdminActivityAction(
                activity,
                "Activity updated by admin",
                buildAdminActivityNotificationContent(activity, before, after, reason)
        );

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
                            .activityTitle(activity == null ? "Activity deleted" : activity.getTitle())
                            .reporterUserId(report.getReporterUserId())
                            .reporterDisplayId(displayId(reporter))
                            .reporterName(resolveName(reporter))
                            .reportedUserId(report.getReportedUserId())
                            .reportedDisplayId(displayId(reported))
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
                .orElseThrow(() -> new BusinessException("Report not found"));
        String status = request == null ? "" : request.getStatus();
        status = status == null ? "" : status.trim().toUpperCase();
        if (!Set.of("RESOLVED", "DISMISSED").contains(status)) {
            throw new BusinessException("Invalid report status");
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
                "Report result",
                ("RESOLVED".equals(status) ? "Your report has been handled. " : "Your report was not accepted. ")
                        + "Note: " + reason,

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
                        .publicId(user.getPublicId())
                        .displayId(user.getDisplayId())
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
                .orElseThrow(() -> new BusinessException("User not found"));
        if (ROLE_ADMIN.equals(user.getRole())) throw new BusinessException("Cannot operate admin account");
        String status = request == null ? "" : request.getStatus();
        status = status == null ? "" : status.trim().toUpperCase();
        if (!Set.of("ACTIVE", "DISABLED").contains(status)) {
            throw new BusinessException("Invalid user status");
        }
        String reason = requireReason(request);
        String previousStatus = user.getStatus();
        if (status.equals(previousStatus)) {
            throw new BusinessException("User is already in this status");
        }
        user.setStatus(status);
        userRepository.save(user);

        if ("DISABLED".equals(status)) {
            disableUserActivityAccess(user, reason, loginUser.userId());
            notificationService.createNotification(
                    user.getId(),
                    Notification.TYPE_ACCOUNT_STATUS_CHANGED,
                    "Account disabled",
                    "Your account has been disabled. Reason: " + reason
                            + ". You can still browse activities and submit an appeal from More.",

                    null,
                    null
            );
        } else {
            notificationService.createNotification(
                    user.getId(),
                    Notification.TYPE_ACCOUNT_STATUS_CHANGED,
                    "Account restored",
                    "Your account has been restored. Note: " + reason
                            + ". Previously cancelled registrations will not be restored automatically.",
                    null,
                    null
            );
        }
        saveAudit(loginUser.userId(), "USER_" + status, "USER", id, reason);
    }

    private void disableUserActivityAccess(User user, String reason, Long adminUserId) {
        LocalDateTime now = LocalDateTime.now();
        List<ActivityRegistration> registrations = activityRegistrationRepository
                .findByUserIdAndStatusIn(user.getId(), Set.of(
                        ActivityRegistration.STATUS_PENDING,
                        ActivityRegistration.STATUS_APPROVED,
                        ActivityRegistration.STATUS_JOINED_GROUP
                ));
        Map<Long, Activity> activities = activityRepository.findAllById(
                        registrations.stream().map(ActivityRegistration::getActivityId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Activity::getId, Function.identity()));
        Set<Long> releasedActivityIds = registrations.stream()
                .filter(registration -> JOINED_STATUSES.contains(registration.getStatus()))
                .map(ActivityRegistration::getActivityId)
                .collect(Collectors.toSet());
        for (ActivityRegistration registration : registrations) {
            registration.setStatus(ActivityRegistration.STATUS_CANCELLED);
            registration.setNoticeCode(ActivityRegistration.NOTICE_CANCELLED);
            registration.setCancellationReasonType("account_disabled");
            registration.setCancellationReasonText("璐﹀彿琚鐞嗗憳绂佺敤");
            registration.setCancelledAt(now);
            Activity activity = activities.get(registration.getActivityId());
            if (activity != null && activity.getCreatorUserId() != null
                    && !activity.getCreatorUserId().equals(user.getId())) {
                notificationService.createNotification(
                        activity.getCreatorUserId(),
                        Notification.TYPE_REGISTRATION_CANCELLED_CREATOR,
                        "鍙備笌鑰呭凡绉诲嚭娲诲姩",
                        resolveName(user) + " was removed from " + activity.getTitle() + " because the account was disabled.",

                        Notification.RELATED_ACTIVITY,
                        activity.getId()
                );
            }
        }
        activityRegistrationRepository.saveAll(registrations);
        releasedActivityIds.stream()
                .map(activities::get)
                .filter(java.util.Objects::nonNull)
                .forEach(this::notifyEarliestPendingAfterDisable);

        List<Activity> createdActivities = activityRepository
                .findByCreatorUserIdAndStatusAndEndAtAfter(
                        user.getId(), "PUBLISHED", now);
        for (Activity activity : createdActivities) {
            Map<String, Object> before = activityAuditState(activity);
            activity.setStatus("HIDDEN");
            Map<String, Object> after = activityAuditState(activity);
            notifyAdminActivityAction(
                    activity,
                    "Activity hidden",
                    activity.getTitle() + " has been hidden because the creator account was disabled. "
                            + "Note: " + reason
            );
            List<ActivityRegistration> relatedRegistrations =
                    activityRegistrationRepository.findByActivityIdOrderByCreatedAtAsc(activity.getId());
            relatedRegistrations.stream()
                    .filter(registration -> ActivityRegistration.STATUS_PENDING.equals(registration.getStatus()))
                    .forEach(registration -> {
                        registration.setStatus(ActivityRegistration.STATUS_CANCELLED);
                        registration.setNoticeCode(ActivityRegistration.NOTICE_CANCELLED);
                        registration.setCancellationReasonType("activity_hidden");
                        registration.setCancellationReasonText("Creator account disabled; activity hidden");
                        registration.setCancelledAt(now);
                        notificationService.createNotification(
                                registration.getUserId(),
                                Notification.TYPE_ADMIN_ACTIVITY_ACTION,
                                "Registration closed",
                                activity.getTitle()
                                        + " was hidden because the creator account was disabled; your pending registration was closed.",
                                Notification.RELATED_ACTIVITY,
                                activity.getId()
                        );
                    });
            activityRegistrationRepository.saveAll(relatedRegistrations);
            saveAudit(
                    adminUserId,
                    "ACTIVITY_HIDE",
                    "ACTIVITY",
                    activity.getId(),
                    "Creator account disabled: " + reason,
                    before,
                    after
            );
        }
        activityRepository.saveAll(createdActivities);
    }

    private void notifyEarliestPendingAfterDisable(Activity activity) {
        if (activity == null || !"PUBLISHED".equals(activity.getStatus())
                || activity.getStartAt() == null
                || !activity.getStartAt().isAfter(LocalDateTime.now())) {
            return;
        }
        int recruitCount = activity.getRecruitCount() == null ? 0 : activity.getRecruitCount();
        long joined = activityRegistrationRepository.countByActivityIdAndStatusIn(
                activity.getId(), JOINED_STATUSES);
        if (recruitCount > 0 && joined >= recruitCount) {
            return;
        }
        activityRegistrationRepository
                .findFirstByActivityIdAndStatusOrderByCreatedAtAsc(
                        activity.getId(), ActivityRegistration.STATUS_PENDING)
                .ifPresent(registration -> notificationService.createNotification(
                        registration.getUserId(),
                        Notification.TYPE_ACTIVITY_SLOT_AVAILABLE,
                        "Activity slot available",
                        activity.getTitle()
                                + " has a released slot. Please wait for creator review.",
                        Notification.RELATED_ACTIVITY,
                        activity.getId()
                ));
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
                .orElseThrow(() -> new BusinessException("Tag not found"));
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
                .orElseThrow(() -> new BusinessException("Tag not found"));
        long usage = countTagUsage(id);
        if (usage > 0) {
            throw new BusinessException("This tag is still used by " + usage + " activities; disable it instead");
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
        String reason = request == null ? "" : request.getReason();
        reason = reason == null ? "" : reason.trim();
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException("Please fill update reason");
        }
        if (reason.length() > 500) {
            throw new BusinessException("Update reason is too long");
        }
        Activity activity = activityRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Activity not found"));
        Map<String, Object> before = activityAuditState(activity);
        List<Long> tagIds = request == null || request.getTagIds() == null
                ? List.of()
                : request.getTagIds().stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (tagIds.isEmpty() || tagIds.size() > 4) {
            throw new BusinessException("Activity must select 1 to 4 tags");
        }
        List<Tag> tags = tagRepository.findAllById(tagIds);
        if (tags.size() != tagIds.size()) {
            throw new BusinessException("鍖呭惈涓嶅瓨鍦ㄧ殑鏍囩");
        }
        Map<Long, Tag> tagMap = tags.stream().collect(Collectors.toMap(Tag::getId, Function.identity()));
        List<String> names = tagIds.stream().map(tagMap::get).map(Tag::getName).toList();
        activity.setTagId(tagIds.get(0));
        activity.setTagIdsJson(writeJson(tagIds));
        activity.setTagsJson(writeJson(names));
        activityRepository.save(activity);
        Map<String, Object> after = activityAuditState(activity);
        notifyAdminActivityAction(
                activity,
                "Activity tags updated by admin",
                buildAdminActivityNotificationContent(activity, before, after, reason)
        );
        saveAudit(
                loginUser.userId(),
                "ACTIVITY_TAGS_UPDATE",
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
    public AdminFeedbackPageVO listFeedback(
            String status, Integer page, Integer pageSize, LoginUser loginUser
    ) {
        requireAdmin(loginUser);
        int p = normalizePage(page);
        int size = normalizePageSize(pageSize);
        String normalizedStatus = StringUtils.hasText(status) ? status.trim().toUpperCase() : null;
        Page<UserFeedback> result = normalizedStatus == null
                ? userFeedbackRepository.findByTypeOrderByCreatedAtDesc(
                        UserFeedback.TYPE_FEEDBACK, PageRequest.of(p - 1, size))
                : userFeedbackRepository.findByTypeAndStatusOrderByCreatedAtDesc(
                        UserFeedback.TYPE_FEEDBACK, normalizedStatus, PageRequest.of(p - 1, size));
        Map<Long, User> users = userRepository.findAllById(
                        result.getContent().stream().map(UserFeedback::getUserId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return AdminFeedbackPageVO.builder()
                .list(result.getContent().stream().map(feedback -> {
                    User user = users.get(feedback.getUserId());
                    return AdminFeedbackItemVO.builder()
                            .id(feedback.getId()).userId(feedback.getUserId()).displayId(displayId(user))
                            .nickname(resolveName(user))
                            .avatarUrl(user == null ? null : user.getAvatarUrl())
                            .type(feedback.getType())
                            .userStatus(user == null ? null : user.getStatus())
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
                .orElseThrow(() -> new BusinessException("Feedback not found"));
        String status = request == null ? "" : request.getStatus();
        status = status == null ? "" : status.trim().toUpperCase();
        if (!Set.of("RESOLVED", "IGNORED").contains(status)) {
            throw new BusinessException("Invalid handling status");
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
                "Feedback result",
                ("RESOLVED".equals(status) ? "Your feedback has been handled. " : "Your feedback was not accepted. ")
                        + "Note: " + note,

                null,
                feedback.getId()
        );
        saveAudit(loginUser.userId(), "FEEDBACK_" + status, "FEEDBACK", id, note);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminFeedbackPageVO listAccountAppeals(
            String status, Integer page, Integer pageSize, LoginUser loginUser
    ) {
        requireAdmin(loginUser);
        int p = normalizePage(page);
        int size = normalizePageSize(pageSize);
        String normalizedStatus = StringUtils.hasText(status) ? status.trim().toUpperCase() : null;
        Page<UserFeedback> result = normalizedStatus == null
                ? userFeedbackRepository.findByTypeOrderByCreatedAtDesc(
                        UserFeedback.TYPE_ACCOUNT_APPEAL, PageRequest.of(p - 1, size))
                : userFeedbackRepository.findByTypeAndStatusOrderByCreatedAtDesc(
                        UserFeedback.TYPE_ACCOUNT_APPEAL, normalizedStatus, PageRequest.of(p - 1, size));
        Map<Long, User> users = userRepository.findAllById(
                        result.getContent().stream().map(UserFeedback::getUserId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));
        return AdminFeedbackPageVO.builder()
                .list(result.getContent().stream().map(appeal -> {
                    User user = users.get(appeal.getUserId());
                    String disableReason = adminAuditLogRepository
                            .findByTargetTypeAndTargetIdOrderByCreatedAtDesc("USER", appeal.getUserId())
                            .stream()
                            .filter(log -> "USER_DISABLED".equals(log.getActionType()))
                            .map(AdminAuditLog::getReason)
                            .findFirst()
                            .orElse("");
                    return AdminFeedbackItemVO.builder()
                            .id(appeal.getId())
                            .userId(appeal.getUserId())
                            .displayId(displayId(user))
                            .nickname(resolveName(user))
                            .avatarUrl(user == null ? null : user.getAvatarUrl())
                            .type(appeal.getType())
                            .userStatus(user == null ? null : user.getStatus())
                            .disableReason(disableReason)
                            .subject(appeal.getSubject())
                            .content(appeal.getContent())
                            .status(appeal.getStatus())
                            .adminNote(appeal.getAdminNote())
                            .createdAt(toEpochMillis(appeal.getCreatedAt()))
                            .handledAt(toEpochMillis(appeal.getHandledAt()))
                            .build();
                }).toList())
                .page(p).pageSize(size).total(result.getTotalElements()).build();
    }

    @Override
    @Transactional
    public void handleAccountAppeal(Long id, AdminActionRequest request, LoginUser loginUser) {
        requireAdmin(loginUser);
        UserFeedback appeal = userFeedbackRepository.findById(id)
                .orElseThrow(() -> new BusinessException("Account appeal not found"));
        if (!UserFeedback.TYPE_ACCOUNT_APPEAL.equals(appeal.getType())) {
            throw new BusinessException("This record is not an account appeal");
        }
        if (!"PENDING".equals(appeal.getStatus())) {
            throw new BusinessException("该申诉已处理");
        }
        String status = request == null ? "" : request.getStatus();
        status = status == null ? "" : status.trim().toUpperCase();
        if (!Set.of("APPROVED", "REJECTED").contains(status)) {
            throw new BusinessException("Invalid appeal status");
        }
        String note = requireReason(request);
        User user = userRepository.findById(appeal.getUserId())
                .orElseThrow(() -> new BusinessException("Appeal user not found"));

        appeal.setStatus(status);
        appeal.setAdminNote(note);
        appeal.setHandledBy(loginUser.userId());
        appeal.setHandledAt(LocalDateTime.now());
        userFeedbackRepository.save(appeal);

        if ("APPROVED".equals(status)) {
            user.setStatus("ACTIVE");
            userRepository.save(user);
        }
        notificationService.createNotification(
                user.getId(),
                Notification.TYPE_ACCOUNT_APPEAL_RESULT,
                "璐﹀彿鐢宠瘔澶勭悊缁撴灉",
                ("APPROVED".equals(status)
                        ? "Your account appeal was approved and your account is restored. "
                        : "Your account appeal was rejected and your account remains disabled. ")
                        + "Note: " + note,
                null,
                appeal.getId()
        );
        saveAudit(
                loginUser.userId(),
                "ACCOUNT_APPEAL_" + status,
                "ACCOUNT_APPEAL",
                appeal.getId(),
                note
        );
        if ("APPROVED".equals(status)) {
            saveAudit(loginUser.userId(), "USER_ACTIVE", "USER", user.getId(),
                    "Account appeal approved: " + note);
        }
    }

    @Override
    @Transactional
    public void sendUserNotification(
            Long userId, AdminNotificationRequest request, LoginUser loginUser
    ) {
        requireAdmin(loginUser);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("User not found"));
        if (ROLE_ADMIN.equals(user.getRole())) {
            throw new BusinessException("不能向管理员账号发送用户通知");
        }
        String title = request == null ? "" : request.getTitle();
        String content = request == null ? "" : request.getContent();
        title = title == null ? "" : title.trim();
        content = content == null ? "" : content.trim();
        if (!StringUtils.hasText(title)) throw new BusinessException("请填写通知标题");
        if (!StringUtils.hasText(content)) throw new BusinessException("请填写通知内容");
        if (title.length() > 100) throw new BusinessException("Notification title is too long");
        if (content.length() > 500) throw new BusinessException("Notification content is too long");
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
                            .displayId(displayId(user))
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
            throw new BusinessException("Please fill tag name");
        }
        if (name.length() > 40) {
            throw new BusinessException("Tag name is too long");
        }
        tagRepository.findByNameIgnoreCase(name).ifPresent(existing -> {
            if (currentId == null || !currentId.equals(existing.getId())) {
                throw new BusinessException("Tag name already exists");
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
            throw new BusinessException("鏍囩鏁版嵁淇濆瓨澶辫触");
        }
    }

    private String resolveName(User user) {
        return user == null || !StringUtils.hasText(user.getNickname()) ? "鏈煡鐢ㄦ埛" : user.getNickname();
    }

    private String requireReason(AdminActionRequest request) {
        String reason = request == null ? "" : request.getReason();
        reason = reason == null ? "" : reason.trim();
        if (!StringUtils.hasText(reason)) throw new BusinessException("Please fill handling reason");
        if (reason.length() > 500) throw new BusinessException("Handling reason is too long");
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
            throw new BusinessException("鏃犳潈璁块棶绠＄悊鍚庡彴");
        }
    }

    private void requireAdminPassword(String password) {
        if (!StringUtils.hasText(password)
                || !adminProperties.getPassword().equals(password)) {
            throw new BusinessException("Admin password is incorrect");
        }
    }

    private Map<String, Object> activityAuditState(Activity activity) {
        Map<String, Object> state = new HashMap<>();
        state.put("status", activity.getStatus());
        state.put("startAt", toEpochMillis(activity.getStartAt()));
        state.put("endAt", toEpochMillis(activity.getEndAt()));
        state.put("locationText", activity.getLocationText());
        state.put("tags", parseList(activity.getTagsJson()));
        return state;
    }

    private String buildAdminActivityNotificationContent(
            Activity activity,
            Map<String, Object> before,
            Map<String, Object> after,
            String reason
    ) {
        List<String> changes = new ArrayList<>();
        addActivityChange(changes, "Status", before.get("status"), after.get("status"), true);
        addActivityChange(changes, "Start time", before.get("startAt"), after.get("startAt"), false);
        addActivityChange(changes, "结束时间", before.get("endAt"), after.get("endAt"), false);
        addActivityChange(changes, "地点", before.get("locationText"), after.get("locationText"), false);
        addActivityChange(changes, "标签", before.get("tags"), after.get("tags"), false);

        String changedText = changes.isEmpty()
                ? "Activity information has been updated"
                : "Changes: " + String.join("; ", changes);
        return "Your activity " + activity.getTitle() + " was updated by an admin. "
                + "Reason: " + reason + ". " + changedText;
    }

    private void addActivityChange(
            List<String> changes,
            String label,
            Object before,
            Object after,
            boolean status
    ) {
        if (java.util.Objects.equals(before, after)) {
            return;
        }
        changes.add(label + ": " + formatActivityChangeValue(before, status)
                + " -> " + formatActivityChangeValue(after, status));
    }

    private String formatActivityChangeValue(Object value, boolean status) {
        if (value == null) {
            return "Not set";
        }
        if (status) {
            return switch (String.valueOf(value)) {
                case "PUBLISHED" -> "姝ｅ父鍙戝竷";
                case "HIDDEN" -> "Hidden";
                case "CANCELLED" -> "Cancelled";
                default -> String.valueOf(value);
            };
        }
        if (value instanceof Number number) {
            return Instant.ofEpochMilli(number.longValue())
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        }
        if (value instanceof List<?> list) {
            return list.isEmpty() ? "None" : list.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(", "));
        }
        String text = String.valueOf(value);
        return StringUtils.hasText(text) ? text : "Not set";
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
            throw new BusinessException("璁板綍绠＄悊鎿嶄綔澶辫触");
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

    private long activeUsersBetween(LocalDateTime start, LocalDateTime end) {
        return scalar("""
                SELECT COUNT(DISTINCT user_id) FROM (
                    SELECT id user_id FROM users WHERE role='USER' AND last_login_at>=? AND last_login_at<?
                    UNION SELECT user_id FROM activity_analytics_events WHERE created_at>=? AND created_at<?
                    UNION SELECT creator_user_id FROM activities WHERE created_at>=? AND created_at<?
                    UNION SELECT user_id FROM activity_registrations WHERE created_at>=? AND created_at<?
                    UNION SELECT user_id FROM activity_favorites WHERE created_at>=? AND created_at<?
                    UNION SELECT reporter_user_id FROM activity_reports WHERE created_at>=? AND created_at<?
                    UNION SELECT user_id FROM user_feedback WHERE created_at>=? AND created_at<?
                ) active
                """, start, end, start, end, start, end, start, end, start, end, start, end, start, end);
    }

    private LocalDate[] analyticsRange(String startDate, String endDate) {
        LocalDate end;
        LocalDate start;
        try {
            end = StringUtils.hasText(endDate) ? LocalDate.parse(endDate) : LocalDate.now();
            start = StringUtils.hasText(startDate) ? LocalDate.parse(startDate) : end.minusDays(29);
        } catch (java.time.format.DateTimeParseException exception) {
            throw new BusinessException("鏃ユ湡鏍煎紡搴斾负yyyy-MM-dd");
        }
        if (start.isAfter(end)) {
            throw new BusinessException("Start date cannot be after end date");
        }
        if (start.isBefore(end.minusDays(365))) {
            throw new BusinessException("Date range cannot exceed 366 days");
        }
        return new LocalDate[]{start, end};
    }

    private long number(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private Double decimal(Object value) {
        if (!(value instanceof Number number)) return null;
        return Math.round(number.doubleValue() * 10D) / 10D;
    }

    private Double rate(long numerator, long denominator) {
        return denominator <= 0 ? 0D : Math.round(numerator * 1000D / denominator) / 10D;
    }

    private String formatTags(Object value) {
        List<String> tags = parseList(text(value));
        return tags.isEmpty() ? "" : String.join(", ", tags);
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

    private String displayId(User user) {
        return user == null ? "" : user.getDisplayId();
    }

    private String generatePublicUserId() {
        for (int i = 0; i < 20; i++) {
            String value = String.valueOf(ThreadLocalRandom.current().nextInt(10000, 100000));
            if (!userRepository.existsByPublicId(value) && !userRepository.existsByDisplayId(value)) {
                return value;
            }
        }
        throw new BusinessException("Failed to generate user id.");
    }
}
