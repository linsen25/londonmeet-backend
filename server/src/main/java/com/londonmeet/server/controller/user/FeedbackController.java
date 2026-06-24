package com.londonmeet.server.controller.user;

import com.londonmeet.common.response.ApiResponse;
import com.londonmeet.pojo.dto.request.FeedbackSubmitRequest;
import com.londonmeet.pojo.entity.UserFeedback;
import com.londonmeet.pojo.vo.FeedbackSubmitVO;
import com.londonmeet.server.repository.UserFeedbackRepository;
import com.londonmeet.server.security.LoginUser;
import com.londonmeet.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/feedback")
@RequiredArgsConstructor
public class FeedbackController {
    private final UserFeedbackRepository feedbackRepository;

    @PostMapping
    public ApiResponse<FeedbackSubmitVO> submit(
            @RequestBody FeedbackSubmitRequest request,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        if (loginUser == null || loginUser.userId() == null) {
            throw new BusinessException("请先登录");
        }
        String subject = request == null ? "" : trim(request.getSubject());
        String content = request == null ? "" : trim(request.getContent());
        if (!StringUtils.hasText(subject)) throw new BusinessException("请填写意见主题");
        if (!StringUtils.hasText(content)) throw new BusinessException("请填写意见内容");
        if (subject.length() > 100) throw new BusinessException("意见主题最多100字");
        if (content.length() > 1000) throw new BusinessException("意见内容最多1000字");
        UserFeedback saved = feedbackRepository.save(UserFeedback.builder()
                .userId(loginUser.userId()).subject(subject).content(content).build());
        return ApiResponse.success(FeedbackSubmitVO.builder()
                .id(saved.getId()).status(saved.getStatus()).build());
    }

    private String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
