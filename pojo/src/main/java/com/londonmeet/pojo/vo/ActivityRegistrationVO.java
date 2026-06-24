package com.londonmeet.pojo.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ActivityRegistrationVO {

    private Long id;

    private Long activityId;

    private String status;

    private String applicationText;

    private Integer noticeCode;
}
