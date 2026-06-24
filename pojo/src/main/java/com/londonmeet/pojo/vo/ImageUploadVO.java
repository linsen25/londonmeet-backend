package com.londonmeet.pojo.vo;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ImageUploadVO {

    private String secureUrl;

    private String publicId;

    private String cloudinaryUrl;

    private Long bytes;

    private Integer width;

    private Integer height;
}
