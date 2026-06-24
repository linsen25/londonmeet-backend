package com.londonmeet.server.service.impl;

import com.londonmeet.common.constant.JwtClaimsConstant;
import com.londonmeet.common.constant.MessageConstant;
import com.londonmeet.common.exception.BusinessException;
import com.londonmeet.common.properties.JwtProperties;
import com.londonmeet.common.utils.JwtUtil;
import com.londonmeet.pojo.dto.request.WxLoginRequest;
import com.londonmeet.pojo.entity.User;
import com.londonmeet.pojo.vo.AvatarUploadVO;
import com.londonmeet.pojo.vo.LoginUserVO;
import com.londonmeet.server.config.UploadProperties;
import com.londonmeet.server.repository.ActivityRepository;
import com.londonmeet.server.repository.UserRepository;
import com.londonmeet.server.security.LoginUser;
import com.londonmeet.server.service.WechatCode2SessionClient;
import com.londonmeet.server.service.WechatCode2SessionClient.WechatSession;
import com.londonmeet.server.service.WxLoginService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WxLoginServiceImpl implements WxLoginService {

    private static final String USER_STATUS_ACTIVE = "ACTIVE";
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    private final UserRepository userRepository;
    private final ActivityRepository activityRepository;
    private final JwtProperties jwtProperties;
    private final WechatCode2SessionClient wechatCode2SessionClient;
    private final UploadProperties uploadProperties;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    @Transactional
    public LoginUserVO login(WxLoginRequest request) {
        String nickname = request.getNickname() == null ? "" : request.getNickname().trim();
        if (!StringUtils.hasText(nickname)) {
            throw new BusinessException("Nickname is required.");
        }

        WechatSession session = wechatCode2SessionClient.exchange(request.getCode());
        storeSessionKey(session);

        User user = userRepository.findByOpenid(session.getOpenid())
                .orElseGet(() -> createUser(session, nickname));

        if (!USER_STATUS_ACTIVE.equals(user.getStatus())) {
            throw new BusinessException(MessageConstant.ACCOUNT_DISABLED);
        }

        user.setNickname(nickname);
        if (StringUtils.hasText(session.getUnionid())) {
            user.setUnionid(session.getUnionid());
        }
        if (!StringUtils.hasText(user.getAvatarUrl())) {
            user.setAvatarUrl(uploadProperties.getDefaultAvatarUrl());
        }
        if (!StringUtils.hasText(user.getCoverUrl())) {
            user.setCoverUrl(uploadProperties.getDefaultCoverUrl());
        }
        user.setLastLoginAt(LocalDateTime.now());

        User savedUser = userRepository.save(user);
        return toLoginUserVO(savedUser, generateToken(savedUser));
    }

    @Override
    @Transactional
    public AvatarUploadVO uploadAvatar(MultipartFile file, LoginUser loginUser) {
        if (loginUser == null || loginUser.userId() == null) {
            throw new BusinessException("Please login first.");
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Avatar file is required.");
        }

        String extension = resolveExtension(file.getOriginalFilename(), file.getContentType());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException("Only jpg, png, and webp avatar images are supported.");
        }

        User user = userRepository.findById(loginUser.userId())
                .orElseThrow(() -> new BusinessException("Login user not found."));

        String filename = user.getId() + "-" + UUID.randomUUID() + "." + extension;
        Path uploadDir = Path.of(uploadProperties.getAvatarDir()).toAbsolutePath().normalize();
        Path target = uploadDir.resolve(filename).normalize();

        if (!target.startsWith(uploadDir)) {
            throw new BusinessException("Invalid avatar upload path.");
        }

        try {
            Files.createDirectories(uploadDir);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new BusinessException("Failed to save avatar file.");
        }

        String avatarUrl = normalizePrefix(uploadProperties.getAvatarUrlPrefix()) + "/" + filename;
        user.setAvatarUrl(avatarUrl);
        userRepository.save(user);
        activityRepository.updateAvatarUrlByCreatorUserId(user.getId(), avatarUrl);

        return AvatarUploadVO.builder()
                .avatarUrl(avatarUrl)
                .build();
    }

    private User createUser(WechatSession session, String nickname) {
        return User.builder()
                .openid(session.getOpenid())
                .unionid(session.getUnionid())
                .nickname(nickname)
                .avatarUrl(uploadProperties.getDefaultAvatarUrl())
                .coverUrl(uploadProperties.getDefaultCoverUrl())
                .role("USER")
                .status(USER_STATUS_ACTIVE)
                .lastLoginAt(LocalDateTime.now())
                .build();
    }

    private void storeSessionKey(WechatSession session) {
        if (!StringUtils.hasText(session.getSessionKey())) {
            return;
        }
        stringRedisTemplate.opsForValue().set(
                "wechat:session:" + session.getOpenid(),
                session.getSessionKey(),
                Duration.ofDays(7)
        );
    }

    private LoginUserVO toLoginUserVO(User user, String token) {
        return LoginUserVO.builder()
                .userId(user.getId())
                .openid(user.getOpenid())
                .nickname(user.getNickname())
                .avatarUrl(user.getAvatarUrl())
                .role(user.getRole())
                .status(user.getStatus())
                .token(token)
                .build();
    }

    private String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtClaimsConstant.USER_ID, user.getId());
        claims.put(JwtClaimsConstant.OPENID, user.getOpenid());
        return JwtUtil.generateToken(jwtProperties.getSecretKey(), jwtProperties.getExpiration(), claims);
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

    private String normalizePrefix(String value) {
        if (!StringUtils.hasText(value)) {
            return "/uploads/avatar";
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
