package com.londonmeet.pojo.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ActivityDetailVO {

    private Long id;

    private String title;

    private String content;

    private String authorName;

    private Long authorUserId;

    private String authorAvatarUrl;

    private String authorMotto;

    private List<String> authorTags;

    private String coverUrl;

    private List<String> imageUrls;

    private List<String> tags;

    private List<Long> tagIds;

    private Long startAt;

    private Long endAt;

    private Integer joinedCount;

    private Integer totalCount;

    private Boolean full;

    private Boolean isCreator;

    private Integer favoriteCount;

    private Boolean favorited;

    private String locationText;

    private String mapImageUrl;

    private String inviteQrUrl;

    private Long qrExpiresAt;

    private Integer editCount;

    private Boolean canEdit;

    private String editBlockedReason;

    private String registrationStatus;

    private Integer noticeCode;
}
