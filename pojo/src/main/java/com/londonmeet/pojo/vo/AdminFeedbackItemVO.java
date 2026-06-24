package com.londonmeet.pojo.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminFeedbackItemVO {
    private Long id;
    private Long userId;
    private String nickname;
    private String avatarUrl;
    private String subject;
    private String content;
    private String status;
    private String adminNote;
    private Long createdAt;
    private Long handledAt;
}
