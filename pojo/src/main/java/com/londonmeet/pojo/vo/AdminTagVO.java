package com.londonmeet.pojo.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminTagVO {
    private Long id;
    private String name;
    private Boolean enabled;
    private Integer sortOrder;
    private Long activityCount;
    private Long createdAt;
    private Long updatedAt;
}
