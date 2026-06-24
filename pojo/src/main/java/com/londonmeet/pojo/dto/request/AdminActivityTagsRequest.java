package com.londonmeet.pojo.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class AdminActivityTagsRequest {
    private List<Long> tagIds;
}
