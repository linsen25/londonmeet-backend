package com.londonmeet.pojo.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class ReviewBatchGoodRequest {
    private Long activityId;
    private List<Long> targetIds;
}
