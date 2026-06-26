package com.londonmeet.server.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.londonmeet.common.exception.BusinessException;
import com.londonmeet.pojo.dto.request.UserProfileUpdateRequest;
import com.londonmeet.pojo.entity.User;
import com.londonmeet.pojo.vo.CoverUploadVO;
import com.londonmeet.pojo.vo.UserProfileStatsVO;
import com.londonmeet.pojo.vo.UserProfileVO;
import com.londonmeet.server.config.UploadProperties;
import com.londonmeet.server.repository.ActivityRepository;
import com.londonmeet.server.repository.UserRepository;
import com.londonmeet.server.security.LoginUser;
import com.londonmeet.server.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {
    private static final String DEFAULT_MOTTO = "你好呀，准备好出去转转了么~";
    private static final String DEFAULT_TAG = "未添加标签";

    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final int MAX_NICKNAME_LENGTH = 30;
    private static final int MAX_MOTTO_LENGTH = 100;
    private static final int MAX_TAGS = 3;
    private static final int MAX_TAG_LENGTH = 10;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private final UserRepository userRepository;
    private final ActivityRepository activityRepository;
    private final UploadProperties uploadProperties;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public UserProfileVO getProfile(LoginUser loginUser) {
        return toProfileVO(requireUser(loginUser));
    }

    @Override
    @Transactional
    public UserProfileVO updateProfile(UserProfileUpdateRequest request, LoginUser loginUser) {
        User user = requireUser(loginUser);
        UserProfileUpdateRequest body = request == null ? new UserProfileUpdateRequest() : request;

        user.setNickname(normalizeNickname(body.getNickname()));
        user.setMotto(normalizeMotto(body.getMotto()));
        user.setTagsJson(toJson(normalizeTags(body.getTags())));

        User savedUser = userRepository.save(user);
        activityRepository.updateAuthorNameByCreatorUserId(savedUser.getId(), savedUser.getNickname());
        return toProfileVO(savedUser);
    }

    @Override
    @Transactional
    public CoverUploadVO uploadCover(MultipartFile file, LoginUser loginUser) {
        User user = requireUser(loginUser);
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Cover file is required.");
        }

        String extension = resolveExtension(file.getOriginalFilename(), file.getContentType());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException("Only jpg, png, and webp cover images are supported.");
        }

        String filename = user.getId() + "-" + UUID.randomUUID() + "." + extension;
        Path uploadDir = Path.of(uploadProperties.getCoverDir()).toAbsolutePath().normalize();
        Path target = uploadDir.resolve(filename).normalize();

        if (!target.startsWith(uploadDir)) {
            throw new BusinessException("Invalid cover upload path.");
        }

        try {
            Files.createDirectories(uploadDir);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new BusinessException("Failed to save cover file.");
        }

        String coverUrl = normalizePrefix(uploadProperties.getCoverUrlPrefix(), "/uploads/cover") + "/" + filename;
        user.setCoverUrl(coverUrl);
        userRepository.save(user);

        return CoverUploadVO.builder()
                .coverUrl(coverUrl)
                .build();
    }

    private User requireUser(LoginUser loginUser) {
        if (loginUser == null || loginUser.userId() == null) {
            throw new BusinessException("Please login first.");
        }
        return userRepository.findById(loginUser.userId())
                .orElseThrow(() -> new BusinessException("Login user not found."));
    }

    private UserProfileVO toProfileVO(User user) {
        LocalDateTime now = LocalDateTime.now();
        UserProfileStatsVO stats = UserProfileStatsVO.builder()
                .myEvents(activityRepository.countByCreatorUserId(user.getId()))
                .ongoing(activityRepository.countByCreatorUserIdAndStatusAndStartAtLessThanEqualAndEndAtAfter(
                        user.getId(),
                        STATUS_PUBLISHED,
                        now,
                        now
                ))
                .build();

        return UserProfileVO.builder()
                .userId(user.getId())
                .nickname(StringUtils.hasText(user.getNickname()) ? user.getNickname() : "MeetFun User")
                .avatarUrl(StringUtils.hasText(user.getAvatarUrl()) ? user.getAvatarUrl() : uploadProperties.getDefaultAvatarUrl())
                .coverUrl(StringUtils.hasText(user.getCoverUrl()) ? user.getCoverUrl() : uploadProperties.getDefaultCoverUrl())
                .motto(StringUtils.hasText(user.getMotto()) ? user.getMotto() : DEFAULT_MOTTO)
                .tags(resolveDisplayTags(user.getTagsJson()))
                .status(user.getStatus())
                .stats(stats)
                .build();
    }

    private String normalizeMotto(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String result = value.trim();
        if (result.length() > MAX_MOTTO_LENGTH) {
            throw new BusinessException("Motto can be at most 100 characters.");
        }
        return result;
    }

    private String normalizeNickname(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException("Nickname is required.");
        }
        String result = value.trim();
        if (result.length() > MAX_NICKNAME_LENGTH) {
            throw new BusinessException("Nickname can be at most 30 characters.");
        }
        return result;
    }

    private List<String> normalizeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String tag : tags) {
            if (!StringUtils.hasText(tag)) {
                continue;
            }
            String trimmed = tag.trim();
            if (trimmed.length() > MAX_TAG_LENGTH) {
                throw new BusinessException("Each profile tag can be at most 10 characters.");
            }
            if (seen.add(trimmed)) {
                result.add(trimmed);
            }
            if (result.size() > MAX_TAGS) {
                throw new BusinessException("At most 3 profile tags are supported.");
            }
        }
        return result;
    }

    private String toJson(List<String> tags) {
        try {
            return objectMapper.writeValueAsString(tags == null ? List.of() : tags);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("Failed to serialize profile tags.");
        }
    }

    private List<String> parseTags(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            List<String> values = objectMapper.readValue(
                    json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
            return normalizeTags(values);
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private List<String> resolveDisplayTags(String json) {
        List<String> tags = parseTags(json);
        return tags.isEmpty() ? List.of(DEFAULT_TAG) : tags;
    }

    private String resolveExtension(String originalFilename, String contentType) {
        String filename = originalFilename == null ? "" : originalFilename;
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex >= 0 && dotIndex < filename.length() - 1) {
            return filename.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        }
        if ("image/png".equalsIgnoreCase(contentType)) {
            return "png";
        }
        if ("image/webp".equalsIgnoreCase(contentType)) {
            return "webp";
        }
        return "jpg";
    }

    private String normalizePrefix(String value, String fallback) {
        if (!StringUtils.hasText(value)) {
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
