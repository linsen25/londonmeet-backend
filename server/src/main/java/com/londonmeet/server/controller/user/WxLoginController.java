package com.londonmeet.server.controller.user;

import com.londonmeet.common.response.ApiResponse;
import com.londonmeet.pojo.dto.request.WxLoginRequest;
import com.londonmeet.pojo.vo.AvatarUploadVO;
import com.londonmeet.pojo.vo.LoginUserVO;
import com.londonmeet.server.security.LoginUser;
import com.londonmeet.server.service.WxLoginService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/wx")
@RequiredArgsConstructor
public class WxLoginController {

    private final WxLoginService wxLoginService;

    @PostMapping("/login")
    public ApiResponse<LoginUserVO> login(@Valid @RequestBody WxLoginRequest request) {
        return ApiResponse.success(wxLoginService.login(request));
    }

    @PostMapping("/upload-avatar")
    public ApiResponse<AvatarUploadVO> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        return ApiResponse.success(wxLoginService.uploadAvatar(file, loginUser));
    }
}
