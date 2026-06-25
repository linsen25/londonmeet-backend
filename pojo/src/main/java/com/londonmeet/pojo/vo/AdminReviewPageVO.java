package com.londonmeet.pojo.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AdminReviewPageVO {

    private List<AdminReviewItemVO> list;
    private Integer page;
    private Integer pageSize;
    private Long total;
}

