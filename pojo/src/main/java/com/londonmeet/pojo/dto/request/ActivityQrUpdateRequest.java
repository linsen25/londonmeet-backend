package com.londonmeet.pojo.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ActivityQrUpdateRequest {

    @NotBlank
    private String inviteQrUrl;

    @NotNull
    @Min(1)
    @Max(7)
    private Integer remainingDays;
}

