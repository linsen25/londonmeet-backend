package com.londonmeet.pojo.dto.request;

import lombok.Data;

@Data
public class AdminLoginRequest {

    private String username;

    private String password;
}
