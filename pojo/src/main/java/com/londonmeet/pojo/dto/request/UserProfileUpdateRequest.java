package com.londonmeet.pojo.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class UserProfileUpdateRequest {

    private String nickname;

    private String motto;

    private List<String> tags;
}
