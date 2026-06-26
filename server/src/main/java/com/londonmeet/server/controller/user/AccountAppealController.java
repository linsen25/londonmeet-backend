package com.londonmeet.server.controller.user;

import com.londonmeet.common.exception.BusinessException;
import com.londonmeet.common.response.ApiResponse;
import com.londonmeet.pojo.dto.request.AccountAppealRequest;
import com.londonmeet.pojo.entity.Notification;
import com.londonmeet.pojo.entity.UserFeedback;
import com.londonmeet.pojo.vo.AccountAppealVO;
import com.londonmeet.server.repository.UserFeedbackRepository;
import com.londonmeet.server.security.LoginUser;
import com.londonmeet.server.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/api/v1/appeals")
@RequiredArgsConstructor
public class AccountAppealController {

    private final UserFeedbackRepository feedbackRepository;
    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<List<AccountAppealVO>> list(
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        requireLogin(loginUser);
        return ApiResponse.success(feedbackRepository
                .findByUserIdAndTypeOrderByCreatedAtDesc(
                        loginUser.userId(), UserFeedback.TYPE_ACCOUNT_APPEAL)
                .stream().map(item -> toVO(item, loginUser.status())).toList());
    }

    @PostMapping
    @Transactional
    public ApiResponse<AccountAppealVO> submit(
            @RequestBody AccountAppealRequest request,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        requireLogin(loginUser);
        if (!"DISABLED".equals(loginUser.status())) {
            throw new BusinessException("当前账号未被禁用，无需提交申诉");
        }
        if (feedbackRepository.existsByUserIdAndTypeAndStatus(
                loginUser.userId(), UserFeedback.TYPE_ACCOUNT_APPEAL, "PENDING")) {
            throw new BusinessException("你已有一条处理中申诉，请勿重复提交");
        }
        String content = request == null || request.getContent() == null
                ? "" : request.getContent().trim();
        if (!StringUtils.hasText(content)) {
            throw new BusinessException("请填写申诉说明");
        }
        if (content.length() > 1000) {
            throw new BusinessException("申诉说明最多1000字");
        }
        UserFeedback saved = feedbackRepository.save(UserFeedback.builder()
                .userId(loginUser.userId())
                .type(UserFeedback.TYPE_ACCOUNT_APPEAL)
                .subject("账号禁用申诉")
                .content(content)
                .build());
        notificationService.createNotification(
                loginUser.userId(),
                Notification.TYPE_FEEDBACK_RECEIVED,
                "账号申诉已提交",
                "你的账号申诉已进入审核，管理员处理后会通知你结果。",
                null,
                saved.getId()
        );
        return ApiResponse.success(toVO(saved, loginUser.status()));
    }

    private void requireLogin(LoginUser loginUser) {
        if (loginUser == null || loginUser.userId() == null) {
            throw new BusinessException("请先登录");
        }
    }

    private AccountAppealVO toVO(UserFeedback feedback, String accountStatus) {
        return AccountAppealVO.builder()
                .id(feedback.getId())
                .content(feedback.getContent())
                .status(feedback.getStatus())
                .adminNote(feedback.getAdminNote())
                .accountStatus(accountStatus)
                .createdAt(toEpochMillis(feedback.getCreatedAt()))
                .handledAt(toEpochMillis(feedback.getHandledAt()))
                .build();
    }

    private Long toEpochMillis(java.time.LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
