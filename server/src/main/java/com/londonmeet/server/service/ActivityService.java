package com.londonmeet.server.service;

import com.londonmeet.pojo.dto.request.ActivityApplyRequest;
import com.londonmeet.pojo.dto.request.ActivityFavoriteRequest;
import com.londonmeet.pojo.dto.request.ActivityCreateRequest;
import com.londonmeet.pojo.dto.request.ActivityQueryRequest;
import com.londonmeet.pojo.dto.request.ActivityReportRequest;
import com.londonmeet.pojo.dto.request.ActivityUpdateRequest;
import com.londonmeet.pojo.dto.request.ActivityQrUpdateRequest;
import com.londonmeet.pojo.dto.request.ActivityCancelRequest;
import com.londonmeet.pojo.dto.request.ActivityCancelRegistrationRequest;
import com.londonmeet.pojo.dto.request.ActivitySearchRequest;
import com.londonmeet.pojo.dto.request.ActivityEventRequest;
import com.londonmeet.pojo.dto.request.ActivityRegistrationReviewRequest;
import com.londonmeet.pojo.vo.ActivityDetailVO;
import com.londonmeet.pojo.vo.ActivityFavoriteVO;
import com.londonmeet.pojo.vo.ActivityPageVO;
import com.londonmeet.pojo.vo.ActivityPostVO;
import com.londonmeet.pojo.vo.ActivityRegistrationVO;
import com.londonmeet.pojo.vo.ActivityReportVO;
import com.londonmeet.pojo.vo.PendingReviewActivityVO;
import com.londonmeet.pojo.vo.PendingReviewVO;
import com.londonmeet.server.security.LoginUser;

import java.util.List;

public interface ActivityService {

    ActivityPostVO createActivity(ActivityCreateRequest request, LoginUser loginUser);

    ActivityPageVO listActivities(ActivityQueryRequest request, LoginUser loginUser);

    ActivityPageVO listMyOngoingActivities(ActivityQueryRequest request, LoginUser loginUser);

    ActivityPageVO listMyCreatedActivities(ActivityQueryRequest request, LoginUser loginUser);

    ActivityDetailVO updateActivity(Long id, ActivityUpdateRequest request, LoginUser loginUser);

    ActivityDetailVO updateActivityQr(Long id, ActivityQrUpdateRequest request, LoginUser loginUser);

    ActivityDetailVO cancelActivity(Long id, ActivityCancelRequest request, LoginUser loginUser);

    void remindCreatorToUpdateQr(Long id, LoginUser loginUser);

    ActivityPageVO searchActivities(ActivitySearchRequest request, LoginUser loginUser);

    ActivityPageVO listFavoriteActivities(ActivityQueryRequest request, LoginUser loginUser);

    ActivityPageVO listHistoryActivities(ActivityQueryRequest request, LoginUser loginUser);

    List<PendingReviewVO> listPendingReviews(LoginUser loginUser);

    List<PendingReviewActivityVO> listPendingReviewActivities(LoginUser loginUser);

    List<PendingReviewVO> listActivityReviewRegistrations(Long activityId, String status, LoginUser loginUser);

    List<PendingReviewVO> listOrganizerBlacklist(Long activityId, LoginUser loginUser);

    ActivityDetailVO getActivityDetail(Long id, LoginUser loginUser);

    ActivityRegistrationVO applyActivity(Long id, ActivityApplyRequest request, LoginUser loginUser);

    ActivityRegistrationVO joinGroup(Long id, LoginUser loginUser);

    ActivityRegistrationVO cancelRegistration(
            Long id,
            ActivityCancelRegistrationRequest request,
            LoginUser loginUser
    );

    ActivityRegistrationVO approveRegistration(
            Long registrationId,
            ActivityRegistrationReviewRequest request,
            LoginUser loginUser
    );

    ActivityRegistrationVO rejectRegistration(
            Long registrationId,
            ActivityRegistrationReviewRequest request,
            LoginUser loginUser
    );

    ActivityRegistrationVO blacklistRegistration(
            Long registrationId,
            ActivityRegistrationReviewRequest request,
            LoginUser loginUser
    );

    void unblockApplicant(Long blacklistId, LoginUser loginUser);

    ActivityFavoriteVO updateFavorite(Long id, ActivityFavoriteRequest request, LoginUser loginUser);

    ActivityReportVO reportActivity(Long id, ActivityReportRequest request, LoginUser loginUser);

    void recordEvents(ActivityEventRequest request, LoginUser loginUser);
}
