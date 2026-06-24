package com.londonmeet.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "upload")
public class UploadProperties {

    private String avatarDir = "uploads/avatar";

    private String avatarUrlPrefix = "/uploads/avatar";

    private String defaultAvatarUrl = "/uploads/avatar/default-avatar.png";

    private String coverDir = "uploads/cover";

    private String coverUrlPrefix = "/uploads/cover";

    private String defaultCoverUrl = "https://dummyimage.com/1200x800/2b2b2b/ffffff.png&text=Cover";
}
