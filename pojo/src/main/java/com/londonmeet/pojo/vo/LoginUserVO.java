package com.londonmeet.pojo.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 登录用户返回对象
 */
@Data
@Builder
public class LoginUserVO {

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 微信 openid
     */
    private String openid;

    /**
     * 用户昵称
     */
    private String nickname;

    /**
     * 用户头像地址
     */
    private String avatarUrl;

    /**
     * 用户角色
     */
    private String role;

    /**
     * 用户状态
     */
    private String status;

    /**
     * JWT Token
     */
    private String token;
}