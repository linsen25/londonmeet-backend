package com.londonmeet.pojo.dto.request;

import lombok.Data;

@Data
public class ActivityQueryRequest {

    private String range = "day";

    private Integer page = 1;

    private Integer pageSize = 20;

    private Boolean refresh = false;

    private String type = "joined";
}
