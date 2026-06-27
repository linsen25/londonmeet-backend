package com.londonmeet.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "upload")
public class UploadProperties {

    private String defaultAvatarUrl = "https://dummyimage.com/300x300/ffffff/111111.png&text=Avatar";

    private String defaultCoverUrl = "";
}
