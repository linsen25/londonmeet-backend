package com.londonmeet.pojo.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ActivityPageVO {

    private List<ActivityPostVO> list;

    private Integer page;

    private Integer pageSize;

    private Boolean hasMore;
}
