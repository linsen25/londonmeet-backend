package com.londonmeet.pojo.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AdminActivityDetailVO {
    private Long id;
    private String title;
    private String content;
    private Long creatorUserId;
    private String authorName;
    private String avatarUrl;
    private String status;
    private String timeStatus;
    private Integer recruitCount;
    private Integer joinedCount;
    private Integer pendingCount;
    private Long startAt;
    private Long endAt;
    private String locationText;
    private List<Long> tagIds;
    private List<String> tags;
    private List<String> imageUrls;
    private Long reportCount;
    private String governanceAction;
    private String governanceReason;
    private Long governedAt;
    private List<AdminAuditLogVO> auditLogs;
    private List<AdminParticipantVO> participants;
}
