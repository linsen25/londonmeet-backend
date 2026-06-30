package com.londonmeet.server.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.londonmeet.common.exception.BusinessException;
import com.londonmeet.pojo.dto.request.UserProfileUpdateRequest;
import com.londonmeet.pojo.entity.ActivityRegistration;
import com.londonmeet.pojo.entity.User;
import com.londonmeet.pojo.vo.CoverUploadVO;
import com.londonmeet.pojo.vo.ImageUploadVO;
import com.londonmeet.pojo.vo.UserProfileStatsVO;
import com.londonmeet.pojo.vo.UserProfileVO;
import com.londonmeet.server.config.UploadProperties;
import com.londonmeet.server.repository.ActivityRepository;
import com.londonmeet.server.repository.ActivityRegistrationRepository;
import com.londonmeet.server.repository.UserRepository;
import com.londonmeet.server.security.LoginUser;
import com.londonmeet.server.service.CloudinaryImageService;
import com.londonmeet.server.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {
    private static final String DEFAULT_MOTTO = "你好呀，准备好出去转转了么~";
    private static final String DEFAULT_TAG = "未添加标签";

    private static final int MAX_NICKNAME_LENGTH = 30;
    private static final int MAX_MOTTO_LENGTH = 100;
    private static final int MAX_TAGS = 3;
    private static final int MAX_TAG_LENGTH = 10;
    private static final long MAX_COVER_BYTES = 8 * 1024 * 1024;
    private static final List<String> ACTIVE_REGISTRATION_STATUSES = List.of(
            ActivityRegistration.STATUS_PENDING,
            ActivityRegistration.STATUS_APPROVED,
            ActivityRegistration.STATUS_JOINED_GROUP
    );

    private final UserRepository userRepository;
    private final ActivityRepository activityRepository;
    private final ActivityRegistrationRepository activityRegistrationRepository;
    private final UploadProperties uploadProperties;
    private final CloudinaryImageService cloudinaryImageService;
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

        ImageUploadVO uploaded = cloudinaryImageService.upload(file, "londonmeet/cover", MAX_COVER_BYTES);
        String coverUrl = uploaded.getSecureUrl();
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
        UserProfileStatsVO stats = UserProfileStatsVO.builder()
                .myEvents(activityRepository.countByCreatorUserId(user.getId()))
                .ongoing(activityRegistrationRepository.countByUserIdAndStatusIn(
                        user.getId(),
                        ACTIVE_REGISTRATION_STATUSES
                ))
                .build();

        return UserProfileVO.builder()
                .userId(user.getId())
                .publicId(user.getPublicId())
                .displayId(user.getDisplayId())
                .nickname(resolveNickname(user))
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

    private String resolveNickname(User user) {
        String nickname = user.getNickname();
        String displayId = user.getDisplayId();
        if (StringUtils.hasText(nickname)
                && nickname.matches("用户\\d{5}")
                && StringUtils.hasText(displayId)
                && !nickname.equals("用户" + displayId)) {
            return "用户" + displayId;
        }
        if (StringUtils.hasText(nickname)) {
            return nickname;
        }
        return StringUtils.hasText(displayId) ? "用户" + displayId : "MeetFun User";
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

}
