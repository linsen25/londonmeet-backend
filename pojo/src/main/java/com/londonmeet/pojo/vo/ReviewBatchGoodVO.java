package com.londonmeet.pojo.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class ReviewBatchGoodVO {
    private List<Long> submittedTargetIds;
    private Map<Long, String> skippedTargets;
}
