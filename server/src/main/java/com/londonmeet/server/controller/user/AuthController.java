package com.londonmeet.server.controller.user;

import com.londonmeet.common.response.ApiResponse;
import com.londonmeet.pojo.dto.request.WechatLoginRequest;
import com.londonmeet.pojo.vo.LoginUserVO;
import com.londonmeet.server.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户认证接口
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 微信小程序登录
     */
    @PostMapping("/wechat-login")
    public ApiResponse<LoginUserVO> wechatLogin(@Valid @RequestBody WechatLoginRequest request) {
        LoginUserVO loginUserVO = authService.wechatLogin(request);
        return ApiResponse.success(loginUserVO);
    }
}