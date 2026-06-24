package com.londonmeet.pojo.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserProfileStatsVO {

    private long myEvents;

    private long ongoing;

    private long likes;
}
