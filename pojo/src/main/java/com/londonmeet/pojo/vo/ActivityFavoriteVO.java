package com.londonmeet.pojo.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ActivityFavoriteVO {

    private Long id;

    private Boolean favorited;

    private Integer favoriteCount;
}
