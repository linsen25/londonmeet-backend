package com.londonmeet.pojo.vo;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class AdminReviewActivityItemVO {
    private Long activityId;
    private String activityTitle;
    private Long creatorUserId;
    private String creatorName;
    private Double activityAverage;
    private Long activityReviewCount;
    private Long memberReviewCount;
    private Long pendingCount;
    private Long participantCount;
    private Long participantReviewedCount;
    private Double creatorRecentAverage;
    private Long creatorRecentReviewCount;
    private Map<String, Double> creatorRecentDimensions;
    private Long endAt;
}
