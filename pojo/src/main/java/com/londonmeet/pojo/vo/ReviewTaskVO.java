package com.londonmeet.pojo.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ReviewTaskVO {

    private String id;

    private String mode;

    private Long activityId;

    private Long targetId;

    private String title;

    private String activityTitle;

    private String name;

    private String avatarUrl;

    private Long startAt;

    private Long endAt;

    private Double overallRating;

    private Double timelinessRating;

    private Integer reviewCount;
}
