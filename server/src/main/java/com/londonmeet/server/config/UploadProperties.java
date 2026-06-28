package com.londonmeet.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "upload")
public class UploadProperties {

    private String defaultAvatarUrl = "https://res.cloudinary.com/ddkqatprj/image/upload/v1782629106/londonmeet/defaultUser.png";

    private String defaultCoverUrl = "";
}
