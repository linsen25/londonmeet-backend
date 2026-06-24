package com.londonmeet.pojo.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FeedbackSubmitVO {
    private Long id;
    private String status;
}
