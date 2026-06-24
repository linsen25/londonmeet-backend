package com.londonmeet.server.service;

import com.londonmeet.pojo.dto.request.WechatLoginRequest;
import com.londonmeet.pojo.vo.LoginUserVO;

/**
 * 用户认证服务接口
 */
public interface AuthService {

    /**
     * 微信小程序登录
     */
    LoginUserVO wechatLogin(WechatLoginRequest request);
}