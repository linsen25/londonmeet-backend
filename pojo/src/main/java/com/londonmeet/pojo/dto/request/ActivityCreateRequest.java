package com.londonmeet.pojo.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ActivityCreateRequest {

    @NotBlank(message = "Activity title is required.")
    @Size(max = 20, message = "Activity title must be 20 characters or less.")
    private String title;

    @NotBlank(message = "Activity content is required.")
    @Size(max = 400, message = "Activity content must be 400 characters or less.")
    private String content;

    private Long tagId;

    @NotEmpty(message = "At least one tag id is required.")
    @Size(max = 4, message = "At most 4 tags are allowed.")
    private List<Long> tagIds;

    @NotNull(message = "Activity start time is required.")
    private Long startAt;

    @NotNull(message = "Activity end time is required.")
    private Long endAt;

    @NotNull(message = "Recruit count is required.")
    @Min(value = 1, message = "Recruit count must be at least 1.")
    private Integer recruitCount;

    @NotBlank(message = "Activity location is required.")
    @Size(max = 500, message = "Activity location must be 500 characters or less.")
    private String locationText;

    private String mapImageUrl;

    @NotEmpty(message = "At least one activity image is required.")
    @Size(max = 4, message = "At most 4 activity images are allowed.")
    private List<String> imageUrls;

    @NotBlank(message = "Invite QR code is required.")
    private String inviteQrUrl;
}
