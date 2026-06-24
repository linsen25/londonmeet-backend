package com.londonmeet.server.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * WeChat Mini Program login settings.
 */
@Data
@Component
@ConfigurationProperties(prefix = "wechat")
public class WechatProperties {

    /**
     * Mini Program app id.
     */
    private String appId;

    /**
     * Mini Program app secret.
     */
    private String secret;

    /**
     * Keep enabled while testing without a real mini program page.
     */
    private boolean mockEnabled = true;
}
