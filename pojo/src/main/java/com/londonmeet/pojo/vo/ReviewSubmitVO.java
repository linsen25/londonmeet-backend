package com.londonmeet.pojo.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReviewSubmitVO {

    private Long id;

    private String mode;

    private Long activityId;

    private Long targetId;

    private Double overallScore;
}
