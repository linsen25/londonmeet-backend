package com.londonmeet.pojo.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminActivityItemVO {

    private Long id;

    private String title;

    private Long creatorUserId;

    private String authorName;

    private String status;

    private Integer joinedCount;

    private Integer pendingCount;

    private Integer recruitCount;

    private Integer progressPct;

    private Long startAt;

    private Long endAt;

    private String locationText;

    private Integer favoriteCount;

    private Long reportCount;
}
