package com.londonmeet.server.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final UploadProperties uploadProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        addUploadHandler(registry, uploadProperties.getAvatarUrlPrefix(), uploadProperties.getAvatarDir(), "/uploads/avatar");
        addUploadHandler(registry, uploadProperties.getCoverUrlPrefix(), uploadProperties.getCoverDir(), "/uploads/cover");
    }

    private void addUploadHandler(ResourceHandlerRegistry registry, String urlPrefix, String dir, String fallbackPrefix) {
        String prefix = normalizePrefix(urlPrefix, fallbackPrefix);
        Path avatarPath = Path.of(dir).toAbsolutePath().normalize();
        registry.addResourceHandler(prefix + "/**")
                .addResourceLocations(avatarPath.toUri().toString());
    }

    private String normalizePrefix(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String result = value.trim();
        if (!result.startsWith("/")) {
            result = "/" + result;
        }
        if (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
