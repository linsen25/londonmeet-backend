package com.londonmeet.server.controller.activity;

import com.londonmeet.common.response.ApiResponse;
import com.londonmeet.pojo.dto.request.ReviewSubmitRequest;
import com.londonmeet.pojo.vo.ReviewSubmitVO;
import com.londonmeet.pojo.vo.ReviewTaskVO;
import com.londonmeet.server.security.LoginUser;
import com.londonmeet.server.service.ReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @GetMapping("/tasks")
    public ApiResponse<List<ReviewTaskVO>> listTasks(
            @RequestParam(required = false) String mode,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(reviewService.listTasks(mode, loginUser));
    }

    @PostMapping
    public ApiResponse<ReviewSubmitVO> submit(
            @RequestBody ReviewSubmitRequest request,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(reviewService.submit(request, loginUser));
    }
}
