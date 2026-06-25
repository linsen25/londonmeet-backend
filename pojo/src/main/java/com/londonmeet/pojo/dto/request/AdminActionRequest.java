package com.londonmeet.pojo.dto.request;

import lombok.Data;

@Data
public class AdminActionRequest {

    private String reason;

    private String status;

    private String password;
}
