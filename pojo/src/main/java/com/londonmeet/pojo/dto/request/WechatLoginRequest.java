package com.londonmeet.pojo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * WeChat login request.
 */
@Data
public class WechatLoginRequest {

    /**
     * WeChat temporary login code from wx.login().
     */
    @NotBlank(message = "WeChat login code is required.")
    private String code;

    /**
     * User nickname.
     */
    @Size(max = 50, message = "Nickname can be at most 50 characters.")
    private String nickname;

    /**
     * User avatar URL.
     */
    @Size(max = 500, message = "Avatar URL can be at most 500 characters.")
    private String avatarUrl;
}
