package com.londonmeet.pojo.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ActivityPostVO {

    private Long id;

    private String title;

    private String authorName;

    private String coverUrl;

    private String avatarUrl;

    private Integer likeCount;

    private Boolean liked;

    private Integer favoriteCount;

    private Boolean favorited;

    private Integer progressPct;

    private Integer joinedCount;

    private Integer totalCount;

    private Long startAt;

    private Long endAt;

    private String progressGif;
}
