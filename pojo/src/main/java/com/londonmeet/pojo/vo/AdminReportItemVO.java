package com.londonmeet.pojo.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminReportItemVO {
    private Long id;
    private Long activityId;
    private String activityTitle;
    private Long reporterUserId;
    private String reporterDisplayId;
    private String reporterName;
    private Long reportedUserId;
    private String reportedDisplayId;
    private String reportedUserName;
    private String reason;
    private String status;
    private String adminNote;
    private Long createdAt;
    private Long handledAt;
}
