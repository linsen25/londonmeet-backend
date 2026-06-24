package com.londonmeet.pojo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WxLoginRequest {

    @NotBlank(message = "Login code is required.")
    private String code;

    @NotBlank(message = "Nickname is required.")
    @Size(max = 50, message = "Nickname must be 50 characters or less.")
    private String nickname;
}
