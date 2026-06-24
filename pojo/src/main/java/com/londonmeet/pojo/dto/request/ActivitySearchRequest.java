package com.londonmeet.pojo.dto.request;

import lombok.Data;

@Data
public class ActivitySearchRequest {

    private String keyword;

    private String tags;

    private Integer page = 1;

    private Integer pageSize = 20;
}
