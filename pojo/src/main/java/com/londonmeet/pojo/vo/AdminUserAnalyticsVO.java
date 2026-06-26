package com.londonmeet.pojo.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AdminUserAnalyticsVO {
    private String startDate;
    private String endDate;
    private Long newUserCount;
    private Long activeUserCount;
    private Long creatorUserCount;
    private Long applicantUserCount;
    private Long creatorAndApplicantCount;
    private Long inactiveUserCount;
    private Double applicationsPerApplicant;
    private List<DailyItem> dailyTrend;

    @Data
    @Builder
    public static class DailyItem {
        private String date;
        private Long newUsers;
        private Long activeUsers;
        private Long activities;
        private Long applications;
        private Long approved;
    }
}
