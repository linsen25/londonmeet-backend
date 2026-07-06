package com.londonmeet.server.controller.admin;

import com.londonmeet.common.response.ApiResponse;
import com.londonmeet.pojo.dto.request.AdminLoginRequest;
import com.londonmeet.pojo.dto.request.AdminActionRequest;
import com.londonmeet.pojo.dto.request.AdminActivityTagsRequest;
import com.londonmeet.pojo.dto.request.AdminActivityUpdateRequest;
import com.londonmeet.pojo.dto.request.AdminTagRequest;
import com.londonmeet.pojo.dto.request.AdminNotificationRequest;
import com.londonmeet.pojo.vo.AdminActivityDetailVO;
import com.londonmeet.pojo.vo.AdminActivityPageVO;
import com.londonmeet.pojo.vo.AdminDashboardVO;
import com.londonmeet.pojo.vo.AdminLoginVO;
import com.londonmeet.pojo.vo.AdminReportPageVO;
import com.londonmeet.pojo.vo.AdminSettingsVO;
import com.londonmeet.pojo.vo.AdminTagVO;
import com.londonmeet.pojo.vo.AdminFeedbackPageVO;
import com.londonmeet.pojo.vo.AdminUserPageVO;
import com.londonmeet.pojo.vo.AdminActivityAnalyticsVO;
import com.londonmeet.pojo.vo.AdminUserAnalyticsVO;
import com.londonmeet.common.exception.BusinessException;
import com.londonmeet.server.security.AdminLoginAttemptLimiter;
import com.londonmeet.server.security.LoginUser;
import com.londonmeet.server.service.AdminService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final AdminLoginAttemptLimiter adminLoginAttemptLimiter;

    @PostMapping("/auth/login")
    public ApiResponse<AdminLoginVO> login(
            @RequestBody AdminLoginRequest request,
            HttpServletRequest servletRequest
    ) {
        String clientIp = resolveClientIp(servletRequest);
        String username = request == null ? "" : request.getUsername();
        adminLoginAttemptLimiter.checkAllowed(username, clientIp);
        try {
            AdminLoginVO result = adminService.login(request);
            adminLoginAttemptLimiter.recordSuccess(username, clientIp);
            return ApiResponse.success(result);
        } catch (BusinessException exception) {
            adminLoginAttemptLimiter.recordFailure(username, clientIp);
            throw exception;
        }
    }

    @GetMapping("/dashboard")
    public ApiResponse<AdminDashboardVO> dashboard(
            @RequestParam(defaultValue = "30") Integer days,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(adminService.getDashboard(days, loginUser));
    }

    @GetMapping("/settings")
    public ApiResponse<AdminSettingsVO> settings(
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(adminService.getSettings(loginUser));
    }

    @GetMapping("/dashboard/export")
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        byte[] file = adminService.exportRecentReport(startDate, endDate, loginUser);
        String filename = "晚些去哪里呀-近30天数据-" + LocalDate.now() + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" +
                                java.net.URLEncoder.encode(filename, java.nio.charset.StandardCharsets.UTF_8)
                                        .replace("+", "%20"))
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(file.length)
                .body(file);
    }

    @GetMapping("/activities")
    public ApiResponse<AdminActivityPageVO> activities(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(adminService.listActivities(
                keyword,
                status,
                page,
                pageSize,
                loginUser
        ));
    }

    @GetMapping("/activities/{id}")
    public ApiResponse<AdminActivityDetailVO> activityDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(adminService.getActivityDetail(id, loginUser));
    }

    @PutMapping("/activities/{id}")
    public ApiResponse<AdminActivityDetailVO> editActivity(
            @PathVariable Long id,
            @RequestBody AdminActivityUpdateRequest request,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(adminService.updateActivity(id, request, loginUser));
    }

    @PutMapping("/activities/{id}/tags")
    public ApiResponse<AdminActivityDetailVO> updateActivityTags(
            @PathVariable Long id,
            @RequestBody AdminActivityTagsRequest request,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(adminService.updateActivityTags(id, request, loginUser));
    }

    @GetMapping("/tags")
    public ApiResponse<List<AdminTagVO>> tags(
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(adminService.listTags(loginUser));
    }

    @PostMapping("/tags")
    public ApiResponse<AdminTagVO> createTag(
            @RequestBody AdminTagRequest request,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(adminService.createTag(request, loginUser));
    }

    @PutMapping("/tags/{id}")
    public ApiResponse<AdminTagVO> updateTag(
            @PathVariable Long id,
            @RequestBody AdminTagRequest request,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(adminService.updateTag(id, request, loginUser));
    }

    @DeleteMapping("/tags/{id}")
    public ApiResponse<Void> deleteTag(
            @PathVariable Long id,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        adminService.deleteTag(id, loginUser);
        return ApiResponse.success();
    }

    @GetMapping("/reports")
    public ApiResponse<AdminReportPageVO> reports(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(adminService.listReports(status, page, pageSize, loginUser));
    }

    @PostMapping("/reports/{id}/handle")
    public ApiResponse<Void> handleReport(
            @PathVariable Long id,
            @RequestBody AdminActionRequest request,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        adminService.handleReport(id, request, loginUser);
        return ApiResponse.success();
    }

    @GetMapping("/users")
    public ApiResponse<AdminUserPageVO> users(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(adminService.listUsers(keyword, status, page, pageSize, loginUser));
    }

    @PostMapping("/users/{id}/status")
    public ApiResponse<Void> updateUserStatus(
            @PathVariable Long id,
            @RequestBody AdminActionRequest request,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        adminService.updateUserStatus(id, request, loginUser);
        return ApiResponse.success();
    }

    @PostMapping("/users/{id}/notifications")
    public ApiResponse<Void> sendUserNotification(
            @PathVariable Long id,
            @RequestBody AdminNotificationRequest request,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        adminService.sendUserNotification(id, request, loginUser);
        return ApiResponse.success();
    }

    @GetMapping("/feedback")
    public ApiResponse<AdminFeedbackPageVO> feedback(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(adminService.listFeedback(status, page, pageSize, loginUser));
    }

    @PostMapping("/feedback/{id}/handle")
    public ApiResponse<Void> handleFeedback(
            @PathVariable Long id,
            @RequestBody AdminActionRequest request,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        adminService.handleFeedback(id, request, loginUser);
        return ApiResponse.success();
    }

    @GetMapping("/appeals")
    public ApiResponse<AdminFeedbackPageVO> appeals(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(
                adminService.listAccountAppeals(status, page, pageSize, loginUser));
    }

    @PostMapping("/appeals/{id}/handle")
    public ApiResponse<Void> handleAppeal(
            @PathVariable Long id,
            @RequestBody AdminActionRequest request,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        adminService.handleAccountAppeal(id, request, loginUser);
        return ApiResponse.success();
    }

    @GetMapping("/analytics/activities")
    public ApiResponse<AdminActivityAnalyticsVO> activityAnalytics(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(adminService.getActivityAnalytics(
                startDate, endDate, page, pageSize, loginUser));
    }

    @GetMapping("/analytics/users")
    public ApiResponse<AdminUserAnalyticsVO> userAnalytics(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(adminService.getUserAnalytics(startDate, endDate, loginUser));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
