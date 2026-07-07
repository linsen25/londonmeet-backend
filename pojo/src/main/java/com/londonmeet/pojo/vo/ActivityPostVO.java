package com.londonmeet.pojo.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ActivityPostVO {

    private Long id;

    private String title;

    private String authorName;

    private String coverUrl;

    private String avatarUrl;

    private Integer favoriteCount;

    private Boolean favorited;

    private Integer progressPct;

    private Integer joinedCount;

    private Integer totalCount;

    private List<Long> tagIds;

    private List<String> tags;

    private String locationText;

    private Long startAt;

    private Long endAt;

    private String progressGif;

    private String registrationStatus;
}
