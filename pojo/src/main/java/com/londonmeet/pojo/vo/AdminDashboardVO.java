package com.londonmeet.pojo.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AdminDashboardVO {

    private Long userCount;

    private Long activityCount;

    private Long registrationCount;

    private Long pendingReportCount;

    private Long upcomingActivityCount;

    private Long ongoingActivityCount;

    private Long endedActivityCount;

    private Long hiddenActivityCount;

    private Long cancelledActivityCount;

    private Long newUserCount;
    private Long activeUserCount;
    private Long newActivityCount;
    private Long periodRegistrationCount;
    private Long approvedRegistrationCount;
    private Long cancelledRegistrationCount;
    private Long pendingAppealCount;

    private List<DailyCount> activityTrend;

    private List<DailyOverview> dailyTrend;

    @Data
    @Builder
    public static class DailyCount {

        private String date;

        private Long count;
    }

    @Data
    @Builder
    public static class DailyOverview {
        private String date;
        private Long newUsers;
        private Long activeUsers;
        private Long activities;
        private Long registrations;
        private Long approved;
    }
}
