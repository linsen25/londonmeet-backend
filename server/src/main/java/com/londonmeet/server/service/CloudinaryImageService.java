package com.londonmeet.server.service;

import com.londonmeet.common.exception.BusinessException;
import com.londonmeet.pojo.vo.ImageUploadVO;
import com.londonmeet.server.config.CloudinaryProperties;
import lombok.RequiredArgsConstructor;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
        PreparedImage preparedImage = prepareImage(file, folder);

        String url = "https://api.cloudinary.com/v1_1/" + cloudinaryProperties.getCloudName() + "/image/upload";
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("upload_preset", cloudinaryProperties.getUploadPreset());
        body.add("folder", normalizeFolder(folder));
        body.add("file", toResource(preparedImage));

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

    private PreparedImage prepareImage(MultipartFile file, String folder) {
        byte[] originalBytes = readBytes(file);
        String contentType = file.getContentType();
        CompressionProfile profile = CompressionProfile.forFolder(folder);

        if ("image/webp".equals(contentType)) {
            return new PreparedImage(originalBytes, safeFilename(file, "upload.webp"));
        }

        BufferedImage source = readImage(originalBytes);
        if (source == null) {
            return new PreparedImage(originalBytes, safeFilename(file, "upload.jpg"));
        }

        try {
            byte[] compressedBytes = compress(source, profile);
            if (compressedBytes.length >= originalBytes.length) {
                return new PreparedImage(originalBytes, safeFilename(file, "upload.jpg"));
            }
            return new PreparedImage(compressedBytes, profile.filename());
        } catch (IOException ex) {
            return new PreparedImage(originalBytes, safeFilename(file, "upload.jpg"));
        }
    }

    private byte[] compress(BufferedImage source, CompressionProfile profile) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Thumbnails.of(source)
                .size(profile.maxDimension(), profile.maxDimension())
                .outputFormat(profile.outputFormat())
                .outputQuality(profile.quality())
                .toOutputStream(output);
        return output.toByteArray();
    }

    private BufferedImage readImage(byte[] bytes) {
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException ex) {
            return null;
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new BusinessException("Failed to read image file.");
        }
    }

    private ByteArrayResource toResource(PreparedImage image) {
        return new ByteArrayResource(image.bytes()) {
            @Override
            public String getFilename() {
                return image.filename();
            }
        };
    }

    private String safeFilename(MultipartFile file, String fallback) {
        String filename = file.getOriginalFilename();
        if (!StringUtils.hasText(filename)) {
            return fallback;
        }
        String safe = filename.replaceAll("[^A-Za-z0-9_.-]", "");
        return StringUtils.hasText(safe) ? safe : fallback;
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

    private record PreparedImage(byte[] bytes, String filename) {
    }

    private record CompressionProfile(int maxDimension, double quality, String outputFormat) {

        private static CompressionProfile forFolder(String folder) {
            String value = StringUtils.hasText(folder) ? folder.toLowerCase() : "";
            if (value.contains("group-qr")) {
                return new CompressionProfile(1200, 0.95, "jpg");
            }
            if (value.contains("avatar")) {
                return new CompressionProfile(800, 0.86, "jpg");
            }
            if (value.contains("cover")) {
                return new CompressionProfile(1600, 0.82, "jpg");
            }
            return new CompressionProfile(1600, 0.82, "jpg");
        }

        private String filename() {
            return "upload." + outputFormat;
        }
    }
}
