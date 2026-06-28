package com.londonmeet.server.service.impl;

import com.londonmeet.common.constant.JwtClaimsConstant;
import com.londonmeet.common.constant.MessageConstant;
import com.londonmeet.common.exception.BusinessException;
import com.londonmeet.common.properties.JwtProperties;
import com.londonmeet.common.utils.JwtUtil;
import com.londonmeet.pojo.dto.request.WechatLoginRequest;
import com.londonmeet.pojo.entity.User;
import com.londonmeet.pojo.vo.LoginUserVO;
import com.londonmeet.server.config.UploadProperties;
import com.londonmeet.server.repository.ActivityRepository;
import com.londonmeet.server.repository.UserRepository;
import com.londonmeet.server.service.AuthService;
import com.londonmeet.server.service.WechatCode2SessionClient;
import com.londonmeet.server.service.WechatCode2SessionClient.WechatSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * User authentication service.
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String USER_STATUS_ACTIVE = "ACTIVE";

    private final UserRepository userRepository;
    private final ActivityRepository activityRepository;
    private final JwtProperties jwtProperties;
    private final UploadProperties uploadProperties;
    private final WechatCode2SessionClient wechatCode2SessionClient;

    /**
     * WeChat Mini Program login.
     */
    @Override
    @Transactional
    public LoginUserVO wechatLogin(WechatLoginRequest request) {
        WechatSession session = wechatCode2SessionClient.exchange(request.getCode());

        User user = userRepository.findByOpenid(session.getOpenid())
                .orElseGet(() -> createUser(session, request));

        if (StringUtils.hasText(session.getUnionid())) {
            user.setUnionid(session.getUnionid());
        }
        String nickname = trimToNull(request.getNickname());
        String avatarUrl = trimToNull(request.getAvatarUrl());
        if (StringUtils.hasText(nickname)) {
            user.setNickname(nickname);
        }
        if (!StringUtils.hasText(user.getNickname())) {
            user.setNickname(defaultNickname(user));
        }
        if (StringUtils.hasText(avatarUrl)) {
            user.setAvatarUrl(avatarUrl);
        }
        if (!StringUtils.hasText(user.getAvatarUrl())) {
            user.setAvatarUrl(uploadProperties.getDefaultAvatarUrl());
        }
        if (!StringUtils.hasText(user.getCoverUrl())) {
            user.setCoverUrl(uploadProperties.getDefaultCoverUrl());
        }
        user.setLastLoginAt(LocalDateTime.now());

        User savedUser = userRepository.save(user);
        syncCreatedActivities(savedUser);
        String token = generateToken(savedUser);

        return LoginUserVO.builder()
                .userId(savedUser.getId())
                .publicId(savedUser.getPublicId())
                .displayId(savedUser.getDisplayId())
                .openid(savedUser.getOpenid())
                .nickname(savedUser.getNickname())
                .avatarUrl(savedUser.getAvatarUrl())
                .role(savedUser.getRole())
                .status(savedUser.getStatus())
                .token(token)
                .build();
    }

    private String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, user.getId());
        claims.put(JwtClaimsConstant.OPENID, user.getOpenid());

        return JwtUtil.generateToken(
                jwtProperties.getSecretKey(),
                jwtProperties.getExpiration(),
                claims
        );
    }

    private User createUser(WechatSession session, WechatLoginRequest request) {
        String userId = generatePublicUserId();
        return User.builder()
                .publicId(userId)
                .displayId(userId)
                .openid(session.getOpenid())
                .unionid(session.getUnionid())
                .nickname(StringUtils.hasText(trimToNull(request.getNickname()))
                        ? trimToNull(request.getNickname()) : defaultNickname(userId))
                .avatarUrl(StringUtils.hasText(trimToNull(request.getAvatarUrl()))
                        ? trimToNull(request.getAvatarUrl()) : uploadProperties.getDefaultAvatarUrl())
                .coverUrl(uploadProperties.getDefaultCoverUrl())
                .role("USER")
                .status(USER_STATUS_ACTIVE)
                .lastLoginAt(LocalDateTime.now())
                .build();
    }

    private String defaultNickname(User user) {
        return defaultNickname(StringUtils.hasText(user.getDisplayId()) ? user.getDisplayId() : user.getPublicId());
    }

    private String defaultNickname(String publicUserId) {
        return "用户" + publicUserId;
    }

    private String generatePublicUserId() {
        for (int i = 0; i < 20; i++) {
            String value = String.valueOf(ThreadLocalRandom.current().nextInt(10000, 100000));
            if (!userRepository.existsByPublicId(value) && !userRepository.existsByDisplayId(value)) {
                return value;
            }
        }
        throw new BusinessException("Failed to generate user id.");
    }

    private void syncCreatedActivities(User user) {
        activityRepository.updateAuthorNameByCreatorUserId(user.getId(), user.getNickname());
        activityRepository.updateAvatarUrlByCreatorUserId(user.getId(), user.getAvatarUrl());
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
