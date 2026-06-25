package com.londonmeet.pojo.vo;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class AdminAuditLogVO {

    private Long id;

    private Long adminUserId;

    private String adminName;

    private String actionType;

    private String reason;

    private Map<String, Object> beforeState;

    private Map<String, Object> afterState;

    private Long createdAt;
}

