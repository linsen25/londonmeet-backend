package com.londonmeet.server.controller.activity;

import com.londonmeet.common.response.ApiResponse;
import com.londonmeet.pojo.dto.request.ActivityApplyRequest;
import com.londonmeet.pojo.dto.request.ActivityCreateRequest;
import com.londonmeet.pojo.dto.request.ActivityFavoriteRequest;
import com.londonmeet.pojo.dto.request.ActivityQueryRequest;
import com.londonmeet.pojo.dto.request.ActivityReportRequest;
import com.londonmeet.pojo.dto.request.ActivityUpdateRequest;
import com.londonmeet.pojo.dto.request.ActivityQrUpdateRequest;
import com.londonmeet.pojo.dto.request.ActivityCancelRegistrationRequest;
import com.londonmeet.pojo.dto.request.ActivityEventRequest;
import com.londonmeet.pojo.vo.ActivityDetailVO;
import com.londonmeet.pojo.vo.ActivityFavoriteVO;
import com.londonmeet.pojo.vo.ActivityPageVO;
import com.londonmeet.pojo.vo.ActivityPostVO;
import com.londonmeet.pojo.vo.ActivityRegistrationVO;
import com.londonmeet.pojo.vo.ActivityReportVO;
import com.londonmeet.pojo.vo.PendingReviewVO;
import com.londonmeet.server.security.LoginUser;
import com.londonmeet.server.service.ActivityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    @PostMapping
    public ApiResponse<ActivityPostVO> createActivity(
            @Valid @RequestBody ActivityCreateRequest request,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(activityService.createActivity(request, loginUser));
    }

    @GetMapping
    public ApiResponse<ActivityPageVO> listActivities(
            ActivityQueryRequest request,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(activityService.listActivities(request, loginUser));
    }

    @GetMapping("/me/ongoing")
    public ApiResponse<ActivityPageVO> listMyOngoingActivities(
            ActivityQueryRequest request,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(activityService.listMyOngoingActivities(request, loginUser));
    }

    @GetMapping("/me/created")
    public ApiResponse<ActivityPageVO> listMyCreatedActivities(
            ActivityQueryRequest request,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(activityService.listMyCreatedActivities(request, loginUser));
    }

    @GetMapping("/me/favorites")
    public ApiResponse<ActivityPageVO> listFavoriteActivities(
            ActivityQueryRequest request,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(activityService.listFavoriteActivities(request, loginUser));
    }

    @GetMapping("/me/history")
    public ApiResponse<ActivityPageVO> listHistoryActivities(
            ActivityQueryRequest request,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(activityService.listHistoryActivities(request, loginUser));
    }

    @GetMapping("/pending-review")
    public ApiResponse<List<PendingReviewVO>> listPendingReviews(
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(activityService.listPendingReviews(loginUser));
    }

    @PostMapping("/registrations/{registrationId}/approve")
    public ApiResponse<ActivityRegistrationVO> approveRegistration(
            @PathVariable Long registrationId,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(activityService.approveRegistration(registrationId, loginUser));
    }

    @PostMapping("/registrations/{registrationId}/reject")
    public ApiResponse<ActivityRegistrationVO> rejectRegistration(
            @PathVariable Long registrationId,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(activityService.rejectRegistration(registrationId, loginUser));
    }

    @GetMapping("/{id}")
    public ApiResponse<ActivityDetailVO> getActivityDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(activityService.getActivityDetail(id, loginUser));
    }

    @PutMapping("/{id}")
    public ApiResponse<ActivityDetailVO> updateActivity(
            @PathVariable Long id,
            @Valid @RequestBody ActivityUpdateRequest request,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(activityService.updateActivity(id, request, loginUser));
    }

    @PutMapping("/{id}/invite-qr")
    public ApiResponse<ActivityDetailVO> updateActivityQr(
            @PathVariable Long id,
            @Valid @RequestBody ActivityQrUpdateRequest request,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(activityService.updateActivityQr(id, request, loginUser));
    }

    @PostMapping("/{id}/invite-qr/remind")
    public ApiResponse<Void> remindCreatorToUpdateQr(
            @PathVariable Long id,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        activityService.remindCreatorToUpdateQr(id, loginUser);
        return ApiResponse.success();
    }

    @PostMapping("/{id}/apply")
    public ApiResponse<ActivityRegistrationVO> applyActivity(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ActivityApplyRequest request,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(activityService.applyActivity(id, request, loginUser));
    }

    @PostMapping("/{id}/join-group")
    public ApiResponse<ActivityRegistrationVO> joinGroup(
            @PathVariable Long id,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(activityService.joinGroup(id, loginUser));
    }

    @PostMapping("/{id}/cancel-registration")
    public ApiResponse<ActivityRegistrationVO> cancelRegistration(
            @PathVariable Long id,
            @Valid @RequestBody ActivityCancelRegistrationRequest request,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(activityService.cancelRegistration(id, request, loginUser));
    }

    @PostMapping("/{id}/favorite")
    public ApiResponse<ActivityFavoriteVO> updateFavorite(
            @PathVariable Long id,
            @RequestBody ActivityFavoriteRequest request,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(activityService.updateFavorite(id, request, loginUser));
    }

    @PostMapping("/{id}/report")
    public ApiResponse<ActivityReportVO> reportActivity(
            @PathVariable Long id,
            @RequestBody ActivityReportRequest request,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(activityService.reportActivity(id, request, loginUser));
    }

    @PostMapping("/events")
    public ApiResponse<Void> recordEvents(
            @RequestBody ActivityEventRequest request,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        activityService.recordEvents(request, loginUser);
        return ApiResponse.success();
    }
}
