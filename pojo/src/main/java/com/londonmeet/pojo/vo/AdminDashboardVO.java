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

    private List<DailyCount> activityTrend;

    @Data
    @Builder
    public static class DailyCount {

        private String date;

        private Long count;
    }
}
