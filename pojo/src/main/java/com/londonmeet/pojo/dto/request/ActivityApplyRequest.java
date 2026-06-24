package com.londonmeet.pojo.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ActivityApplyRequest {

    @Size(max = 100, message = "Application text must be at most 100 characters.")
    private String applicationText;
}
