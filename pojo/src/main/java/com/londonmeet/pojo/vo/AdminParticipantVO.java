package com.londonmeet.pojo.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminParticipantVO {
    private Long registrationId;
    private Long userId;
    private String nickname;
    private String avatarUrl;
    private String status;
    private String applicationText;
    private Long appliedAt;
}
