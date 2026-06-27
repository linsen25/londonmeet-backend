package com.londonmeet.server.service;

import com.londonmeet.common.exception.BusinessException;
import com.londonmeet.pojo.vo.ImageUploadVO;
import com.londonmeet.server.config.CloudinaryProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CloudinaryImageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private final CloudinaryProperties cloudinaryProperties;

    public ImageUploadVO upload(MultipartFile file, String folder, long maxBytes) {
        validateImage(file, maxBytes);
        validateCloudinaryConfig();

        String url = "https://api.cloudinary.com/v1_1/" + cloudinaryProperties.getCloudName() + "/image/upload";
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("upload_preset", cloudinaryProperties.getUploadPreset());
        body.add("folder", normalizeFolder(folder));
        body.add("file", toResource(file));

        Map<?, ?> response = RestClient.create()
                .post()
                .uri(url)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(body)
                .retrieve()
                .body(Map.class);

        if (response == null || !StringUtils.hasText(asString(response.get("secure_url")))) {
            throw new BusinessException("Cloud image upload failed.");
        }

        String publicId = asString(response.get("public_id"));
        String format = asString(response.get("format"));

        return ImageUploadVO.builder()
                .secureUrl("/api/v1/uploads/image-proxy?publicId=" + publicId + "&format=" + format)
                .cloudinaryUrl(asString(response.get("secure_url")))
                .publicId(publicId)
                .bytes(asLong(response.get("bytes")))
                .width(asInteger(response.get("width")))
                .height(asInteger(response.get("height")))
                .build();
    }

    private void validateImage(MultipartFile file, long maxBytes) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Image file is required.");
        }
        if (file.getSize() > maxBytes) {
            throw new BusinessException("Image file is too large.");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new BusinessException("Only jpg, png, and webp images are supported.");
        }
    }

    private void validateCloudinaryConfig() {
        if (!StringUtils.hasText(cloudinaryProperties.getCloudName())
                || !StringUtils.hasText(cloudinaryProperties.getUploadPreset())) {
            throw new BusinessException("Cloudinary is not configured.");
        }
    }

    private ByteArrayResource toResource(MultipartFile file) {
        try {
            return new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "upload.jpg";
                }
            };
        } catch (IOException ex) {
            throw new BusinessException("Failed to read image file.");
        }
    }

    private String normalizeFolder(String folder) {
        String value = StringUtils.hasText(folder) ? folder.trim() : "londonmeet/dev";
        return value.replaceAll("[^A-Za-z0-9_\\-/]", "");
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }
}
