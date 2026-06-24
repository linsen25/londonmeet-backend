package com.londonmeet.pojo.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ActivityLikeVO {

    private Long id;

    private Boolean liked;

    private Integer likeCount;
}
