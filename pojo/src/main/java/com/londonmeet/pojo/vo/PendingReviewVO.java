package com.londonmeet.pojo.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PendingReviewVO {

    private Long registrationId;

    private Long activityId;

    private Long userId;

    private String displayId;

    private String activityTitle;

    private String nickname;

    private String avatarUrl;

    private String applicationText;

    private String status;

    private String reviewReasonType;

    private String reviewReasonText;

    private Long reviewedAt;

    private Long blacklistId;

    private Long blacklistedAt;

    private Long appliedAt;
}
