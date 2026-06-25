package com.londonmeet.pojo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ActivityCancelRegistrationRequest {

    @NotBlank
    @Size(max = 30)
    private String reasonType;

    @Size(max = 100)
    private String reasonText;
}

