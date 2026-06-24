package com.londonmeet.pojo.dto.request;

import lombok.Data;

/**
 * WeChat login request.
 */
@Data
public class WechatLoginRequest {

    /**
     * WeChat temporary login code from wx.login().
     */
    private String code;

    /**
     * Test-only openid. This keeps backend login testable before the mini program
     * page is implemented.
     */
    private String openid;

    /**
     * User nickname.
     */
    private String nickname;

    /**
     * User avatar URL.
     */
    private String avatarUrl;
}
