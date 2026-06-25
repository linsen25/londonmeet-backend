package com.londonmeet.pojo.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ActivityUpdateRequest {

    @NotBlank
    @Size(max = 400)
    private String content;

    @NotEmpty
    @Size(max = 4)
    private List<Long> tagIds;

    @NotNull
    private Long startAt;

    @NotNull
    private Long endAt;

    @NotNull
    @Min(1)
    private Integer recruitCount;

    @NotBlank
    @Size(max = 500)
    private String locationText;

    private String mapImageUrl;

    @NotEmpty
    @Size(max = 4)
    private List<String> imageUrls;
}

