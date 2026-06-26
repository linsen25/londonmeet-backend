package com.londonmeet.server.service;

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
import com.londonmeet.pojo.vo.AdminReviewPageVO;
import com.londonmeet.pojo.vo.AdminReviewActivityPageVO;
import com.londonmeet.pojo.vo.AdminUserPageVO;
import com.londonmeet.pojo.vo.AdminActivityAnalyticsVO;
import com.londonmeet.pojo.vo.AdminUserAnalyticsVO;
import com.londonmeet.server.security.LoginUser;

import java.util.List;


public interface AdminService {

    AdminLoginVO login(AdminLoginRequest request);

    AdminDashboardVO getDashboard(Integer days, LoginUser loginUser);

    AdminSettingsVO getSettings(LoginUser loginUser);

    byte[] exportRecentReport(String startDate, String endDate, LoginUser loginUser);

    AdminActivityPageVO listActivities(
            String keyword,
            String status,
            Integer page,
            Integer pageSize,
            LoginUser loginUser
    );

    AdminActivityDetailVO getActivityDetail(Long id, LoginUser loginUser);

    AdminActivityDetailVO updateActivity(
            Long id, AdminActivityUpdateRequest request, LoginUser loginUser
    );

    AdminReportPageVO listReports(String status, Integer page, Integer pageSize, LoginUser loginUser);

    void handleReport(Long id, AdminActionRequest request, LoginUser loginUser);

    AdminUserPageVO listUsers(
            String keyword, String status, Integer page, Integer pageSize, LoginUser loginUser
    );

    void updateUserStatus(Long id, AdminActionRequest request, LoginUser loginUser);

    List<AdminTagVO> listTags(LoginUser loginUser);

    AdminTagVO createTag(AdminTagRequest request, LoginUser loginUser);

    AdminTagVO updateTag(Long id, AdminTagRequest request, LoginUser loginUser);

    void deleteTag(Long id, LoginUser loginUser);

    AdminActivityDetailVO updateActivityTags(
            Long id, AdminActivityTagsRequest request, LoginUser loginUser
    );

    AdminFeedbackPageVO listFeedback(
            String status, Integer page, Integer pageSize, LoginUser loginUser
    );

    void handleFeedback(Long id, AdminActionRequest request, LoginUser loginUser);

    AdminFeedbackPageVO listAccountAppeals(
            String status, Integer page, Integer pageSize, LoginUser loginUser
    );

    void handleAccountAppeal(Long id, AdminActionRequest request, LoginUser loginUser);

    AdminReviewActivityPageVO listReviewActivities(
            String targetType,
            String status,
            String keyword,
            Integer page,
            Integer pageSize,
            LoginUser loginUser
    );

    AdminReviewPageVO listActivityReviewDetails(Long activityId, LoginUser loginUser);

    void updateReviewStatus(Long id, AdminActionRequest request, LoginUser loginUser);

    void sendUserNotification(
            Long userId, AdminNotificationRequest request, LoginUser loginUser
    );

    AdminActivityAnalyticsVO getActivityAnalytics(
            String startDate, String endDate, Integer page, Integer pageSize, LoginUser loginUser);

    AdminUserAnalyticsVO getUserAnalytics(
            String startDate, String endDate, LoginUser loginUser);
}
