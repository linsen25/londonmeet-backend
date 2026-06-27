package com.londonmeet.pojo.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class UserProfileVO {

    private Long userId;

    private String publicId;

    private String nickname;

    private String avatarUrl;

    private String coverUrl;

    private String motto;

    private List<String> tags;

    private String status;

    private UserProfileStatsVO stats;
}
