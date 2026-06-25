package com.londonmeet.pojo.dto.request;

import lombok.Data;

@Data
public class AdminActivityUpdateRequest {

    private String status;

    private Long startAt;

    private Long endAt;

    private String locationText;

    private String reason;

    private String password;
}

