package com.londonmeet.pojo.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class ReviewSubmitRequest {

    private String mode;

    private Long activityId;

    private Long targetId;

    private List<ReviewScoreRequest> scores;

    private String reason;
}
