package com.londonmeet.pojo.vo;

import com.londonmeet.pojo.dto.request.ReviewScoreRequest;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AdminReviewItemVO {

    private Long id;
    private String targetType;
    private Long activityId;
    private String activityTitle;
    private Long reviewerUserId;
    private String reviewerName;
    private Long targetId;
    private String targetName;
    private Double overallScore;
    private List<ReviewScoreRequest> scores;
    private String status;
    private String adminNote;
    private Long handledBy;
    private String handledByName;
    private Long createdAt;
    private Long updatedAt;
    private Long handledAt;
}

