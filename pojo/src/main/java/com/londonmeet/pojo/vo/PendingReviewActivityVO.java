package com.londonmeet.pojo.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PendingReviewActivityVO {

    private Long activityId;

    private String activityTitle;

    private Long startAt;

    private Long endAt;

    private String locationText;

    private Integer totalRegistrationCount;

    private Integer pendingCount;

    private Integer approvedCount;

    private Integer rejectedCount;

    private Boolean hasUnread;

    private Long latestAppliedAt;
}
