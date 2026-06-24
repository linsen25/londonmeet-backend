package com.londonmeet.server.controller.user;

import com.londonmeet.common.exception.BusinessException;
import com.londonmeet.common.response.ApiResponse;
import com.londonmeet.pojo.vo.ImageUploadVO;
import com.londonmeet.server.config.CloudinaryProperties;
import com.londonmeet.server.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/uploads")
@RequiredArgsConstructor
public class UploadController {

    private static final long MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );
    private static final Set<String> ALLOWED_FORMATS = Set.of("jpg", "jpeg", "png", "webp");

    private final CloudinaryProperties cloudinaryProperties;

    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ImageUploadVO> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", required = false) String folder,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        if (loginUser == null || loginUser.userId() == null) {
            throw new BusinessException("Please login first.");
        }
        validateImage(file);

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

        return ApiResponse.success(ImageUploadVO.builder()
                .secureUrl("/api/v1/uploads/image-proxy?publicId=" + publicId + "&format=" + format)
                .cloudinaryUrl(asString(response.get("secure_url")))
                .publicId(publicId)
                .bytes(asLong(response.get("bytes")))
                .width(asInteger(response.get("width")))
                .height(asInteger(response.get("height")))
                .build());
    }

    @GetMapping("/image-proxy")
    public ResponseEntity<byte[]> imageProxy(
            @RequestParam("publicId") String publicId,
            @RequestParam(value = "format", required = false) String format
    ) {
        String normalizedPublicId = normalizePublicId(publicId);
        String normalizedFormat = normalizeFormat(format);
        String suffix = StringUtils.hasText(normalizedFormat) ? "." + normalizedFormat : "";
        String cloudinaryUrl = "https://res.cloudinary.com/"
                + cloudinaryProperties.getCloudName()
                + "/image/upload/"
                + normalizedPublicId
                + suffix;

        ResponseEntity<byte[]> response = RestClient.create()
                .get()
                .uri(cloudinaryUrl)
                .retrieve()
                .toEntity(byte[].class);

        MediaType contentType = response.getHeaders().getContentType();
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
                .contentType(contentType == null ? MediaType.APPLICATION_OCTET_STREAM : contentType)
                .body(response.getBody());
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Image file is required.");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new BusinessException("Image file must be 5MB or less.");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw new BusinessException("Only jpg, png, and webp images are supported.");
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

    private String normalizePublicId(String publicId) {
        if (!StringUtils.hasText(publicId)) {
            throw new BusinessException("Image public id is required.");
        }
        String value = publicId.trim().replaceAll("[^A-Za-z0-9_\\-/]", "");
        if (!StringUtils.hasText(value)) {
            throw new BusinessException("Image public id is invalid.");
        }
        return value;
    }

    private String normalizeFormat(String format) {
        if (!StringUtils.hasText(format)) {
            return "";
        }
        String value = format.trim().toLowerCase();
        return ALLOWED_FORMATS.contains(value) ? value : "";
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
