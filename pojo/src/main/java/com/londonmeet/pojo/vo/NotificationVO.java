package com.londonmeet.pojo.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class NotificationVO {

    private Long id;

    private String type;

    private String title;

    private String content;

    private String relatedType;

    private Long relatedId;

    private boolean read;

    private Long readAt;

    private Long createdAt;
}
