package com.londonmeet.pojo.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class ActivityEventRequest {
    private String eventType;
    private List<Long> activityIds;
}
