package com.londonmeet.pojo.dto.request;

import lombok.Data;

@Data
public class ReviewScoreRequest {

    private String key;

    private String label;

    private Double value;
}
