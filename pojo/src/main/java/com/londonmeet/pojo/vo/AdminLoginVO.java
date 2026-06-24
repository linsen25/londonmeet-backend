package com.londonmeet.pojo.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminLoginVO {

    private String token;

    private String username;

    private String role;
}
