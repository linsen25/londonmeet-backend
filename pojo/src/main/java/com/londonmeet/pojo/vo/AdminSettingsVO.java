package com.londonmeet.pojo.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminSettingsVO {
    private Integer activityDetailRetentionDays;
    private String retentionDescription;
    private String exportDescription;
}
