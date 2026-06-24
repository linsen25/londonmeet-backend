package com.londonmeet.pojo.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminUserItemVO {
    private Long id;
    private String nickname;
    private String avatarUrl;
    private String status;
    private Long createdAt;
    private Long lastLoginAt;
    private Long createdActivityCount;
    private Long joinedActivityCount;
    private Long reportCount;
}
