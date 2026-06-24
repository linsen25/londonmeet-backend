package com.londonmeet.pojo.dto.request;

import lombok.Data;

@Data
public class AdminTagRequest {
    private String name;
    private Boolean enabled;
    private Integer sortOrder;
}
