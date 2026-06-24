package com.londonmeet.server.controller.user;

import com.londonmeet.common.response.ApiResponse;
import com.londonmeet.pojo.dto.request.UserProfileUpdateRequest;
import com.londonmeet.pojo.vo.CoverUploadVO;
import com.londonmeet.pojo.vo.UserProfileVO;
import com.londonmeet.server.security.LoginUser;
import com.londonmeet.server.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/user/profile")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping
    public ApiResponse<UserProfileVO> getProfile(@AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.success(userProfileService.getProfile(loginUser));
    }

    @PutMapping
    public ApiResponse<UserProfileVO> updateProfile(
            @RequestBody UserProfileUpdateRequest request,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(userProfileService.updateProfile(request, loginUser));
    }

    @PostMapping("/cover")
    public ApiResponse<CoverUploadVO> uploadCover(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(userProfileService.uploadCover(file, loginUser));
    }
}
