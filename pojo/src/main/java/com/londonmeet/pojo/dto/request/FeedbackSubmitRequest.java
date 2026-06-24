package com.londonmeet.pojo.dto.request;

import lombok.Data;

@Data
public class FeedbackSubmitRequest {
    private String subject;
    private String content;
}
