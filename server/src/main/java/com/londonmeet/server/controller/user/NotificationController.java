package com.londonmeet.server.controller.user;

import com.londonmeet.common.response.ApiResponse;
import com.londonmeet.pojo.vo.NotificationUnreadCountVO;
import com.londonmeet.pojo.vo.NotificationVO;
import com.londonmeet.server.security.LoginUser;
import com.londonmeet.server.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<List<NotificationVO>> listNotifications(
            @RequestParam(required = false) Integer pageSize,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(notificationService.listNotifications(pageSize, loginUser));
    }

    @GetMapping("/unread-count")
    public ApiResponse<NotificationUnreadCountVO> unreadCount(
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(notificationService.unreadCount(loginUser));
    }

    @PostMapping("/{id}/read")
    public ApiResponse<NotificationVO> markRead(
            @PathVariable Long id,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(notificationService.markRead(id, loginUser));
    }

    @PostMapping("/read-all")
    public ApiResponse<NotificationUnreadCountVO> markAllRead(
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(notificationService.markAllRead(loginUser));
    }
}
