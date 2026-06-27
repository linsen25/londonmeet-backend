package com.londonmeet.server.controller.user;

import com.londonmeet.common.exception.BusinessException;
import com.londonmeet.common.response.ApiResponse;
import com.londonmeet.pojo.vo.ImageUploadVO;
import com.londonmeet.server.config.CloudinaryProperties;
import com.londonmeet.server.security.LoginUser;
import com.londonmeet.server.service.CloudinaryImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.client.RestClient;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/uploads")
@RequiredArgsConstructor
public class UploadController {

    private static final long MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_FORMATS = Set.of("jpg", "jpeg", "png", "webp");

    private final CloudinaryProperties cloudinaryProperties;
    private final CloudinaryImageService cloudinaryImageService;

    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ImageUploadVO> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", required = false) String folder,
            @AuthenticationPrincipal LoginUser loginUser
    ) {
        if (loginUser == null || loginUser.userId() == null) {
            throw new BusinessException("Please login first.");
        }
        return ApiResponse.success(cloudinaryImageService.upload(file, folder, MAX_IMAGE_BYTES));
    }

    @GetMapping("/image-proxy")
    public ResponseEntity<byte[]> imageProxy(
            @RequestParam("publicId") String publicId,
            @RequestParam(value = "format", required = false) String format
    ) {
        String normalizedPublicId = normalizePublicId(publicId);
        String normalizedFormat = normalizeFormat(format);
        validateCloudinaryConfig();
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

    private void validateCloudinaryConfig() {
        if (!StringUtils.hasText(cloudinaryProperties.getCloudName())) {
            throw new BusinessException("Cloudinary is not configured.");
        }
    }

}
