package com.londonmeet.pojo.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AccountAppealVO {
    private Long id;
    private String content;
    private String status;
    private String adminNote;
    private String accountStatus;
    private Long createdAt;
    private Long handledAt;
}
