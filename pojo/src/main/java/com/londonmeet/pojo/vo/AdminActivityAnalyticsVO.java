package com.londonmeet.pojo.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AdminActivityAnalyticsVO {
    private String startDate;
    private String endDate;
    private Long total;
    private List<ActivityItem> list;

    @Data
    @Builder
    public static class ActivityItem {
        private Long activityId;
        private String title;
        private String creatorName;
        private String tags;
        private Long exposureUsers;
        private Long detailUsers;
        private Long favoriteUsers;
        private Long applicationCount;
        private Long approvedCount;
        private Long joinedGroupCount;
        private Long groupQrUsers;
        private Long cancelledCount;
        private Long reviewCount;
        private Double averageRating;
        private Long reportCount;
        private Double exposureToDetailRate;
        private Double detailToApplyRate;
        private Double approvalRate;
        private Double groupJoinRate;
        private Double groupQrViewRate;
    }
}
