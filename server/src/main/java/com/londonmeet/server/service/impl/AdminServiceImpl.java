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
import com.londonmeet.pojo.vo.AdminReviewActivityItemVO;
import com.londonmeet.pojo.vo.AdminReviewActivityPageVO;
import com.londonmeet.pojo.vo.AdminActivityAnalyticsVO;
import com.londonmeet.pojo.vo.AdminUserAnalyticsVO;
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
import java.util.LinkedHashMap;
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
                       (SELECT COUNT(*) FROM activity_reviews v
                         WHERE v.activity_id=a.id AND v.target_type='activity'
                           AND v.created_at>=? AND v.created_at<?) review_count,
                       (SELECT AVG(v.overall_score) FROM activity_reviews v
                         WHERE v.activity_id=a.id AND v.target_type='activity' AND v.status='NORMAL'
                           AND v.created_at>=? AND v.created_at<?) average_rating,
                       (SELECT COUNT(*) FROM activity_reports rp
                         WHERE rp.activity_id=a.id AND rp.created_at>=? AND rp.created_at<?) report_count
                FROM activities a
                WHERE a.created_at < ? AND a.end_at >= ?
                ORDER BY a.created_at DESC
                LIMIT ? OFFSET ?
                """,
                start, end, start, end, start, end, start, end, start, end,
                start, end,
                start, end, start, end, start, end, start, end, start, end,
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
                        .reviewCount(number(row, "review_count"))
                        .averageRating(decimal(row.get("average_rating")))
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
                    UNION SELECT reviewer_user_id FROM activity_reviews WHERE created_at>=? AND created_at<?
                    UNION SELECT reporter_user_id FROM activity_reports WHERE created_at>=? AND created_at<?
                    UNION SELECT user_id FROM user_feedback WHERE created_at>=? AND created_at<?
                ) active
                """, start, end, start, end, start, end, start, end, start, end, start, end, start, end, start, end);
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
                .pendingReviewCount(scalar("SELECT COUNT(*) FROM activity_reviews WHERE status='PENDING'"))
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
                .retentionDescription("活动结束30天后清理图片、二维码、报名和举报明细；活动骨架与评价永久保留。")
                .exportDescription("报告不在服务器保存，点击下载时按所选日期实时生成Excel文件。")
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

            Sheet summary = workbook.createSheet("核心指标汇总");
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
                    scalar("SELECT COUNT(*) FROM activity_registrations WHERE approved_at >= ? AND approved_at < ?", start, end));
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
                    "列表查看人数", "详情访问人数", "查看群码人数", "评价人数", "活动平均分",
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
                           (SELECT COUNT(DISTINCT e.user_id) FROM activity_analytics_events e
                               WHERE e.activity_id=a.id AND e.event_type='EXPOSURE'
                                 AND e.created_at>=? AND e.created_at<?) exposure_users,
                           (SELECT COUNT(DISTINCT e.user_id) FROM activity_analytics_events e
                               WHERE e.activity_id=a.id AND e.event_type='DETAIL_VIEW'
                                 AND e.created_at>=? AND e.created_at<?) detail_users,
                           (SELECT COUNT(DISTINCT e.user_id) FROM activity_analytics_events e
                               WHERE e.activity_id=a.id AND e.event_type='QR_OPEN'
                                 AND e.created_at>=? AND e.created_at<?) qr_users,
                           (SELECT COUNT(*) FROM activity_reviews v WHERE v.activity_id=a.id
                               AND v.target_type='activity' AND v.created_at>=? AND v.created_at<?) review_count,
                           (SELECT AVG(v.overall_score) FROM activity_reviews v WHERE v.activity_id=a.id
                               AND v.target_type='activity' AND v.status='NORMAL'
                               AND v.created_at>=? AND v.created_at<?) average_rating,
                           (SELECT l.action_type FROM admin_audit_logs l
                               WHERE l.target_type = 'ACTIVITY' AND l.target_id = a.id
                               ORDER BY l.created_at DESC LIMIT 1) governance_action,
                           (SELECT l.reason FROM admin_audit_logs l
                               WHERE l.target_type = 'ACTIVITY' AND l.target_id = a.id
                               ORDER BY l.created_at DESC LIMIT 1) governance_reason
                    FROM activities a
                    WHERE a.created_at >= ? OR a.end_at >= ?
                    ORDER BY a.created_at DESC
                    """, start, end, start, end, start, end, start, end, start, end, start, start);
            int detailRow = 1;
            for (Map<String, Object> activity : activities) {
                addRow(details, detailRow++, null,
                        activity.get("id"), activity.get("title"), activity.get("author_name"),
                        text(activity.get("created_at")), text(activity.get("start_at")), text(activity.get("end_at")),
                        activity.get("status"), activity.get("recruit_count"), activity.get("registration_count"),
                        activity.get("joined_count"), activity.get("pending_count"), activity.get("favorite_count"),
                        activity.get("report_count"), activity.get("exposure_users"), activity.get("detail_users"),
                        activity.get("qr_users"), activity.get("review_count"), activity.get("average_rating"),
                        activity.get("location_text"),
                        activity.get("governance_action"), activity.get("governance_reason"));
            }
            for (int column = 0; column < headers.length; column++) {
                details.setColumnWidth(column, Math.min(column == 1 || column == 13 || column == 15 ? 32 : 18, 40) * 256);
            }
            details.createFreezePane(0, 1);
            details.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(0, detailRow - 1), 0, headers.length - 1));

            Sheet daily = workbook.createSheet("每日趋势");
            addRow(daily, 0, headerStyle,
                    "日期", "新增用户", "活跃用户", "新增活动", "报名数", "审核通过", "取消报名");
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

            Sheet users = workbook.createSheet("用户行为汇总");
            addRow(users, 0, headerStyle, "指标", "数据");
            int userRow = 1;
            userRow = addMetric(users, userRow, "新增用户",
                    scalar("SELECT COUNT(*) FROM users WHERE role='USER' AND created_at>=? AND created_at<?", start, end));
            userRow = addMetric(users, userRow, "活跃用户",
                    activeUsersBetween(start, end));
            userRow = addMetric(users, userRow, "发起活动用户",
                    scalar("SELECT COUNT(DISTINCT creator_user_id) FROM activities WHERE created_at>=? AND created_at<?", start, end));
            userRow = addMetric(users, userRow, "报名用户",
                    scalar("SELECT COUNT(DISTINCT user_id) FROM activity_registrations WHERE created_at>=? AND created_at<?", start, end));
            userRow = addMetric(users, userRow, "收藏用户",
                    scalar("SELECT COUNT(DISTINCT user_id) FROM activity_favorites WHERE created_at>=? AND created_at<?", start, end));
            userRow = addMetric(users, userRow, "查看活动详情用户",
                    scalar("SELECT COUNT(DISTINCT user_id) FROM activity_analytics_events WHERE event_type='DETAIL_VIEW' AND created_at>=? AND created_at<?", start, end));
            addMetric(users, userRow, "打开群二维码用户",
                    scalar("SELECT COUNT(DISTINCT user_id) FROM activity_analytics_events WHERE event_type='QR_OPEN' AND created_at>=? AND created_at<?", start, end));
            users.setColumnWidth(0, 30 * 256);
            users.setColumnWidth(1, 20 * 256);

            Sheet governance = workbook.createSheet("治理数据");
            addRow(governance, 0, headerStyle, "指标", "数据");
            int governanceRow = 1;
            governanceRow = addMetric(governance, governanceRow, "新增举报",
                    scalar("SELECT COUNT(*) FROM activity_reports WHERE created_at>=? AND created_at<?", start, end));
            governanceRow = addMetric(governance, governanceRow, "已处理举报",
                    scalar("SELECT COUNT(*) FROM activity_reports WHERE handled_at>=? AND handled_at<?", start, end));
            governanceRow = addMetric(governance, governanceRow, "新增意见",
                    scalar("SELECT COUNT(*) FROM user_feedback WHERE type='FEEDBACK' AND created_at>=? AND created_at<?", start, end));
            governanceRow = addMetric(governance, governanceRow, "新增账号申诉",
                    scalar("SELECT COUNT(*) FROM user_feedback WHERE type='ACCOUNT_APPEAL' AND created_at>=? AND created_at<?", start, end));
            governanceRow = addMetric(governance, governanceRow, "待审核低分",
                    scalar("SELECT COUNT(*) FROM activity_reviews WHERE status='PENDING' AND created_at>=? AND created_at<?", start, end));
            governanceRow = addMetric(governance, governanceRow, "已忽略评价",
                    scalar("SELECT COUNT(*) FROM activity_reviews WHERE status='EXCLUDED' AND handled_at>=? AND handled_at<?", start, end));
            addMetric(governance, governanceRow, "禁用账号操作",
                    scalar("SELECT COUNT(*) FROM admin_audit_logs WHERE action_type='USER_DISABLED' AND created_at>=? AND created_at<?", start, end));
            governance.setColumnWidth(0, 30 * 256);
            governance.setColumnWidth(1, 20 * 256);

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
                .orElseThrow(() -> new BusinessException("活动不存在"));
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
        Map<String, Object> after = activityAuditState(activity);
        notifyAdminActivityAction(
                activity,
                "活动信息已由管理员调整",
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
                        .publicId(user.getPublicId())
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
        String previousStatus = user.getStatus();
        if (status.equals(previousStatus)) {
            throw new BusinessException("用户已处于该状态");
        }
        user.setStatus(status);
        userRepository.save(user);

        if ("DISABLED".equals(status)) {
            disableUserActivityAccess(user, reason, loginUser.userId());
            notificationService.createNotification(
                    user.getId(),
                    Notification.TYPE_ACCOUNT_STATUS_CHANGED,
                    "账号已被禁用",
                    "你的账号已被管理员禁用。原因：" + reason
                            + "。你仍可浏览活动、查看通知，并可在“更多-我要申诉”提交账号申诉。",
                    null,
                    null
            );
        } else {
            notificationService.createNotification(
                    user.getId(),
                    Notification.TYPE_ACCOUNT_STATUS_CHANGED,
                    "账号已恢复使用",
                    "你的账号已恢复正常使用。处理说明：" + reason
                            + "。此前取消的报名不会自动恢复；被隐藏的活动需由管理员单独恢复。",
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
            registration.setCancellationReasonText("账号被管理员禁用");
            registration.setCancelledAt(now);
            Activity activity = activities.get(registration.getActivityId());
            if (activity != null && activity.getCreatorUserId() != null
                    && !activity.getCreatorUserId().equals(user.getId())) {
                notificationService.createNotification(
                        activity.getCreatorUserId(),
                        Notification.TYPE_REGISTRATION_CANCELLED_CREATOR,
                        "参与者已移出活动",
                        resolveName(user) + " 因账号被禁用，已从「" + activity.getTitle()
                                + "」的报名名单中移除。",
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
                    "活动已下架",
                    "「" + activity.getTitle() + "」因发起人账号被禁用已由管理员下架。"
                            + "处理说明：" + reason
            );
            List<ActivityRegistration> relatedRegistrations =
                    activityRegistrationRepository.findByActivityIdOrderByCreatedAtAsc(activity.getId());
            relatedRegistrations.stream()
                    .filter(registration -> ActivityRegistration.STATUS_PENDING.equals(registration.getStatus()))
                    .forEach(registration -> {
                        registration.setStatus(ActivityRegistration.STATUS_CANCELLED);
                        registration.setNoticeCode(ActivityRegistration.NOTICE_CANCELLED);
                        registration.setCancellationReasonType("activity_hidden");
                        registration.setCancellationReasonText("发起人账号被禁用，活动已下架");
                        registration.setCancelledAt(now);
                        notificationService.createNotification(
                                registration.getUserId(),
                                Notification.TYPE_ADMIN_ACTIVITY_ACTION,
                                "报名已关闭",
                                "「" + activity.getTitle()
                                        + "」因发起人账号被禁用已下架，你的待审核报名已关闭。",
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
                    "发起人账号被禁用：" + reason,
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
                        "活动有名额释放",
                        "「" + activity.getTitle()
                                + "」因参与者账号状态变化释放了名额。你是最早报名的待审核参与者，请等待发起者审核。",
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
        String reason = request == null ? "" : request.getReason();
        reason = reason == null ? "" : reason.trim();
        if (!StringUtils.hasText(reason)) {
            throw new BusinessException("请填写修改原因");
        }
        if (reason.length() > 500) {
            throw new BusinessException("修改原因最多 500 字");
        }
        Activity activity = activityRepository.findById(id)
                .orElseThrow(() -> new BusinessException("活动不存在"));
        Map<String, Object> before = activityAuditState(activity);
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
        Map<String, Object> after = activityAuditState(activity);
        notifyAdminActivityAction(
                activity,
                "活动标签已由管理员调整",
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
                            .id(feedback.getId()).userId(feedback.getUserId())
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
                .orElseThrow(() -> new BusinessException("账号申诉不存在"));
        if (!UserFeedback.TYPE_ACCOUNT_APPEAL.equals(appeal.getType())) {
            throw new BusinessException("该记录不是账号申诉");
        }
        if (!"PENDING".equals(appeal.getStatus())) {
            throw new BusinessException("该申诉已处理");
        }
        String status = request == null ? "" : request.getStatus();
        status = status == null ? "" : status.trim().toUpperCase();
        if (!Set.of("APPROVED", "REJECTED").contains(status)) {
            throw new BusinessException("申诉处理状态无效");
        }
        String note = requireReason(request);
        User user = userRepository.findById(appeal.getUserId())
                .orElseThrow(() -> new BusinessException("申诉用户不存在"));

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
                "账号申诉处理结果",
                ("APPROVED".equals(status)
                        ? "你的账号申诉已通过，账号现已恢复正常使用。"
                        : "你的账号申诉未通过，账号仍处于禁用状态。")
                        + "处理说明：" + note,
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
                    "账号申诉通过：" + note);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public AdminReviewActivityPageVO listReviewActivities(
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
                ? targetType.trim().toLowerCase() : null;
        String normalizedStatus = StringUtils.hasText(status)
                && Set.of(ActivityReview.STATUS_PENDING, ActivityReview.STATUS_NORMAL,
                ActivityReview.STATUS_EXCLUDED).contains(status.trim().toUpperCase())
                ? status.trim().toUpperCase() : null;
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        Page<Activity> result = activityRepository.findAdminReviewActivities(
                normalizedKeyword,
                normalizedType,
                normalizedStatus,
                PageRequest.of(p - 1, size, Sort.by(Sort.Direction.DESC, "endAt"))
        );
        List<Activity> activities = result.getContent();
        if (activities.isEmpty()) {
            return AdminReviewActivityPageVO.builder()
                    .list(List.of()).page(p).pageSize(size).total(result.getTotalElements()).build();
        }
        List<Long> activityIds = activities.stream().map(Activity::getId).toList();
        Map<Long, List<ActivityReview>> reviewsByActivity = activityReviewRepository
                .findByActivityIdIn(activityIds).stream()
                .collect(Collectors.groupingBy(ActivityReview::getActivityId));
        Map<Long, Long> participantCounts = activityRegistrationRepository
                .findByActivityIdInAndStatusIn(activityIds, JOINED_STATUSES).stream()
                .collect(Collectors.groupingBy(
                        ActivityRegistration::getActivityId, Collectors.counting()));
        LocalDateTime since = LocalDateTime.now().minusDays(30);
        List<AdminReviewActivityItemVO> items = activities.stream().map(activity -> {
            List<ActivityReview> reviews = reviewsByActivity.getOrDefault(activity.getId(), List.of());
            List<ActivityReview> validActivityReviews = reviews.stream()
                    .filter(review -> ActivityReview.TARGET_ACTIVITY.equals(review.getTargetType()))
                    .filter(review -> ActivityReview.STATUS_NORMAL.equals(review.getStatus()))
                    .toList();
            List<ActivityReview> creatorRecent = activity.getCreatorUserId() == null
                    ? List.of()
                    : activityReviewRepository.findRecentActivityReviewsByCreatorUserId(
                            activity.getCreatorUserId(), since);
            return AdminReviewActivityItemVO.builder()
                    .activityId(activity.getId())
                    .activityTitle(activity.getTitle())
                    .creatorUserId(activity.getCreatorUserId())
                    .creatorName(activity.getAuthorName())
                    .activityAverage(averageReviews(validActivityReviews))
                    .activityReviewCount((long) validActivityReviews.size())
                    .memberReviewCount(reviews.stream()
                            .filter(review -> ActivityReview.TARGET_MEMBER.equals(review.getTargetType()))
                            .count())
                    .pendingCount(reviews.stream()
                            .filter(review -> ActivityReview.STATUS_PENDING.equals(review.getStatus()))
                            .count())
                    .participantCount(activity.getArchivedParticipantCount() == null
                            ? participantCounts.getOrDefault(activity.getId(), 0L)
                            : activity.getArchivedParticipantCount())
                    .participantReviewedCount(reviews.stream()
                            .filter(review -> ActivityReview.TARGET_MEMBER.equals(review.getTargetType()))
                            .filter(review -> java.util.Objects.equals(
                                    review.getReviewerUserId(), activity.getCreatorUserId()))
                            .map(ActivityReview::getTargetId).distinct().count())
                    .creatorRecentAverage(averageReviews(creatorRecent))
                    .creatorRecentReviewCount((long) creatorRecent.size())
                    .creatorRecentDimensions(averageReviewDimensions(creatorRecent))
                    .endAt(toEpochMillis(activity.getEndAt()))
                    .build();
        }).toList();
        return AdminReviewActivityPageVO.builder()
                .list(items).page(p).pageSize(size).total(result.getTotalElements()).build();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminReviewPageVO listActivityReviewDetails(Long activityId, LoginUser loginUser) {
        requireAdmin(loginUser);
        activityRepository.findById(activityId)
                .orElseThrow(() -> new BusinessException("活动不存在"));
        List<ActivityReview> reviews =
                activityReviewRepository.findByActivityIdOrderByCreatedAtDesc(activityId);
        return AdminReviewPageVO.builder()
                .list(toAdminReviewItems(reviews))
                .page(1).pageSize(reviews.size()).total((long) reviews.size()).build();
    }

    private List<AdminReviewItemVO> toAdminReviewItems(List<ActivityReview> reviews) {
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

        return reviews.stream().map(review -> {
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
                    .reason(review.getReason())
                    .batchGood(Boolean.TRUE.equals(review.getBatchGood()))
                    .status(review.getStatus())
                    .adminNote(review.getAdminNote())
                    .handledBy(review.getHandledBy())
                    .handledByName(handler == null ? null : resolveName(handler))
                    .createdAt(toEpochMillis(review.getCreatedAt()))
                    .updatedAt(toEpochMillis(review.getUpdatedAt()))
                    .handledAt(toEpochMillis(review.getHandledAt()))
                    .build();
        }).toList();
    }

    private Double averageReviews(List<ActivityReview> reviews) {
        return reviews.isEmpty() ? null : Math.round(
                reviews.stream().mapToDouble(review -> review.getOverallScore().doubleValue())
                        .average().orElse(0) * 10.0) / 10.0;
    }

    private Map<String, Double> averageReviewDimensions(List<ActivityReview> reviews) {
        Map<String, List<Double>> values = new LinkedHashMap<>();
        reviews.forEach(review -> parseReviewScores(review.getScoresJson()).forEach(score ->
                values.computeIfAbsent(score.getKey(), ignored -> new ArrayList<>())
                        .add(score.getValue())));
        Map<String, Double> result = new LinkedHashMap<>();
        values.forEach((key, scores) -> result.put(key,
                Math.round(scores.stream().mapToDouble(Double::doubleValue)
                        .average().orElse(0) * 10.0) / 10.0));
        return result;
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
        boolean pendingApproved = ActivityReview.STATUS_PENDING.equals(before.get("status"))
                && ActivityReview.STATUS_NORMAL.equals(status);
        notificationService.createNotification(
                review.getReviewerUserId(),
                Notification.TYPE_REVIEW_MODERATED,
                ActivityReview.STATUS_EXCLUDED.equals(status)
                        ? "评价未计入评分"
                        : (pendingApproved ? "低分评价审核通过" : "评价已恢复"),
                (ActivityReview.STATUS_EXCLUDED.equals(status)
                        ? "你提交的一条评价已被管理员隐藏。"
                        : (pendingApproved
                            ? "你提交的低分评价已审核通过并计入评分。"
                            : "你提交的一条评价已由管理员恢复。"))
                        + "处理说明：" + reason,
                Notification.RELATED_ACTIVITY,
                review.getActivityId()
        );
        if (pendingApproved) {
            Activity activity = activityRepository.findById(review.getActivityId()).orElse(null);
            Long receiverUserId = ActivityReview.TARGET_ACTIVITY.equals(review.getTargetType())
                    ? (activity == null ? null : activity.getCreatorUserId())
                    : review.getTargetId();
            if (receiverUserId != null && !receiverUserId.equals(review.getReviewerUserId())) {
                User reviewer = userRepository.findById(review.getReviewerUserId()).orElse(null);
                notificationService.createNotification(
                        receiverUserId,
                        Notification.TYPE_REVIEW_RECEIVED,
                        ActivityReview.TARGET_ACTIVITY.equals(review.getTargetType())
                                ? "活动收到新评价" : "你收到一条成员评价",
                        resolveName(reviewer) + " 对「"
                                + (activity == null ? "相关活动" : activity.getTitle())
                                + "」提交的评价已审核通过。",
                        Notification.RELATED_ACTIVITY,
                        review.getActivityId()
                );
            }
        }

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
        addActivityChange(changes, "状态", before.get("status"), after.get("status"), true);
        addActivityChange(changes, "开始时间", before.get("startAt"), after.get("startAt"), false);
        addActivityChange(changes, "结束时间", before.get("endAt"), after.get("endAt"), false);
        addActivityChange(changes, "地点", before.get("locationText"), after.get("locationText"), false);
        addActivityChange(changes, "标签", before.get("tags"), after.get("tags"), false);

        String changedText = changes.isEmpty()
                ? "活动信息已调整"
                : "修改内容：" + String.join("；", changes);
        return "你的活动「" + activity.getTitle() + "」已由管理员修改。"
                + "修改原因：" + reason + "。" + changedText;
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
        changes.add(label + "：" + formatActivityChangeValue(before, status)
                + " → " + formatActivityChangeValue(after, status));
    }

    private String formatActivityChangeValue(Object value, boolean status) {
        if (value == null) {
            return "未设置";
        }
        if (status) {
            return switch (String.valueOf(value)) {
                case "PUBLISHED" -> "正常发布";
                case "HIDDEN" -> "已隐藏";
                case "CANCELLED" -> "已取消";
                default -> String.valueOf(value);
            };
        }
        if (value instanceof Number number) {
            return Instant.ofEpochMilli(number.longValue())
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        }
        if (value instanceof List<?> list) {
            return list.isEmpty() ? "无" : list.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining("、"));
        }
        String text = String.valueOf(value);
        return StringUtils.hasText(text) ? text : "未设置";
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

    private long activeUsersBetween(LocalDateTime start, LocalDateTime end) {
        return scalar("""
                SELECT COUNT(DISTINCT user_id) FROM (
                    SELECT id user_id FROM users WHERE role='USER' AND last_login_at>=? AND last_login_at<?
                    UNION SELECT user_id FROM activity_analytics_events WHERE created_at>=? AND created_at<?
                    UNION SELECT creator_user_id FROM activities WHERE created_at>=? AND created_at<?
                    UNION SELECT user_id FROM activity_registrations WHERE created_at>=? AND created_at<?
                    UNION SELECT user_id FROM activity_favorites WHERE created_at>=? AND created_at<?
                    UNION SELECT reviewer_user_id FROM activity_reviews WHERE created_at>=? AND created_at<?
                    UNION SELECT reporter_user_id FROM activity_reports WHERE created_at>=? AND created_at<?
                    UNION SELECT user_id FROM user_feedback WHERE created_at>=? AND created_at<?
                ) active
                """, start, end, start, end, start, end, start, end, start, end, start, end, start, end, start, end);
    }

    private LocalDate[] analyticsRange(String startDate, String endDate) {
        LocalDate end;
        LocalDate start;
        try {
            end = StringUtils.hasText(endDate) ? LocalDate.parse(endDate) : LocalDate.now();
            start = StringUtils.hasText(startDate) ? LocalDate.parse(startDate) : end.minusDays(29);
        } catch (java.time.format.DateTimeParseException exception) {
            throw new BusinessException("日期格式应为yyyy-MM-dd");
        }
        if (start.isAfter(end)) {
            throw new BusinessException("开始日期不能晚于结束日期");
        }
        if (start.isBefore(end.minusDays(365))) {
            throw new BusinessException("单次最多查询366天");
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
        return tags.isEmpty() ? "" : String.join("、", tags);
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
