package com.londonmeet.server.service.impl;

import com.londonmeet.common.constant.JwtClaimsConstant;
import com.londonmeet.common.constant.MessageConstant;
import com.londonmeet.common.exception.BusinessException;
import com.londonmeet.common.properties.JwtProperties;
import com.londonmeet.common.utils.JwtUtil;
import com.londonmeet.pojo.dto.request.WxLoginRequest;
import com.londonmeet.pojo.entity.User;
import com.londonmeet.pojo.vo.AvatarUploadVO;
import com.londonmeet.pojo.vo.ImageUploadVO;
import com.londonmeet.pojo.vo.LoginUserVO;
import com.londonmeet.server.config.UploadProperties;
import com.londonmeet.server.repository.ActivityRepository;
import com.londonmeet.server.repository.UserRepository;
import com.londonmeet.server.security.LoginUser;
import com.londonmeet.server.service.CloudinaryImageService;
import com.londonmeet.server.service.WechatCode2SessionClient;
import com.londonmeet.server.service.WechatCode2SessionClient.WechatSession;
import com.londonmeet.server.service.WxLoginService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WxLoginServiceImpl implements WxLoginService {

    private static final String USER_STATUS_ACTIVE = "ACTIVE";
    private static final long MAX_AVATAR_BYTES = 5 * 1024 * 1024;

    private final UserRepository userRepository;
    private final ActivityRepository activityRepository;
    private final JwtProperties jwtProperties;
    private final WechatCode2SessionClient wechatCode2SessionClient;
    private final UploadProperties uploadProperties;
    private final CloudinaryImageService cloudinaryImageService;

    @Override
    @Transactional
    public LoginUserVO login(WxLoginRequest request) {
        String nickname = request.getNickname() == null ? "" : request.getNickname().trim();
        if (!StringUtils.hasText(nickname)) {
            throw new BusinessException("Nickname is required.");
        }

        WechatSession session = wechatCode2SessionClient.exchange(request.getCode());

        User user = userRepository.findByOpenid(session.getOpenid())
                .orElseGet(() -> createUser(session, nickname));

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

        User user = userRepository.findById(loginUser.userId())
                .orElseThrow(() -> new BusinessException("Login user not found."));

        ImageUploadVO uploaded = cloudinaryImageService.upload(file, "londonmeet/avatar", MAX_AVATAR_BYTES);
        String avatarUrl = uploaded.getSecureUrl();
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

    private LoginUserVO toLoginUserVO(User user, String token) {
        return LoginUserVO.builder()
                .userId(user.getId())
                .publicId(user.getPublicId())
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

}
