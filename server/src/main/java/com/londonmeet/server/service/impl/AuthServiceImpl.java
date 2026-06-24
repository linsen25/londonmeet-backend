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
import com.londonmeet.server.repository.UserRepository;
import com.londonmeet.server.service.AuthService;
import com.londonmeet.server.service.WechatCode2SessionClient;
import com.londonmeet.server.service.WechatCode2SessionClient.WechatSession;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * User authentication service.
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);
    private static final String USER_STATUS_ACTIVE = "ACTIVE";
    private static final String MOCK_OPENID_PREFIX = "mock-openid-";

    private final UserRepository userRepository;
    private final JwtProperties jwtProperties;
    private final UploadProperties uploadProperties;
    private final WechatCode2SessionClient wechatCode2SessionClient;

    /**
     * WeChat Mini Program login.
     */
    @Override
    @Transactional
    public LoginUserVO wechatLogin(WechatLoginRequest request) {
        log.info("WECHAT_LOGIN STEP 1 resolve session");
        WechatSession session = resolveWechatSession(request);
        log.info("WECHAT_LOGIN STEP 2 session openid={}", session.getOpenid());

        User user = userRepository.findByOpenid(session.getOpenid())
                .orElseGet(() -> createUser(session, request));
        log.info("WECHAT_LOGIN STEP 3 user loaded id={}", user.getId());

        if (!USER_STATUS_ACTIVE.equals(user.getStatus())) {
            throw new BusinessException(MessageConstant.ACCOUNT_DISABLED);
        }

        if (StringUtils.hasText(session.getUnionid())) {
            user.setUnionid(session.getUnionid());
        }
        if (StringUtils.hasText(request.getNickname())) {
            user.setNickname(request.getNickname());
        }
        if (StringUtils.hasText(request.getAvatarUrl())) {
            user.setAvatarUrl(request.getAvatarUrl());
        }
        if (!StringUtils.hasText(user.getAvatarUrl())) {
            user.setAvatarUrl(uploadProperties.getDefaultAvatarUrl());
        }
        if (!StringUtils.hasText(user.getCoverUrl())) {
            user.setCoverUrl(uploadProperties.getDefaultCoverUrl());
        }
        user.setLastLoginAt(LocalDateTime.now());

        log.info("WECHAT_LOGIN STEP 4 save user");
        User savedUser = userRepository.save(user);
        log.info("WECHAT_LOGIN STEP 5 saved user id={}", savedUser.getId());

        log.info("WECHAT_LOGIN STEP 6 generate token");
        String token = generateToken(savedUser);
        log.info("WECHAT_LOGIN STEP 7 token generated");

        return LoginUserVO.builder()
                .userId(savedUser.getId())
                .openid(savedUser.getOpenid())
                .nickname(savedUser.getNickname())
                .avatarUrl(savedUser.getAvatarUrl())
                .role(savedUser.getRole())
                .status(savedUser.getStatus())
                .token(token)
                .build();
    }

    private WechatSession resolveWechatSession(WechatLoginRequest request) {
        if (StringUtils.hasText(request.getOpenid())) {
            return new WechatSession(request.getOpenid(), null, null);
        }
        return wechatCode2SessionClient.exchange(request.getCode());
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
        return User.builder()
                .openid(session.getOpenid())
                .unionid(session.getUnionid())
                .nickname(StringUtils.hasText(request.getNickname()) ? request.getNickname() : defaultNickname(session))
                .avatarUrl(StringUtils.hasText(request.getAvatarUrl()) ? request.getAvatarUrl() : uploadProperties.getDefaultAvatarUrl())
                .coverUrl(uploadProperties.getDefaultCoverUrl())
                .role("USER")
                .status(USER_STATUS_ACTIVE)
                .lastLoginAt(LocalDateTime.now())
                .build();
    }

    private String defaultNickname(WechatSession session) {
        return session.getOpenid().startsWith(MOCK_OPENID_PREFIX) ? "Mock User" : "New User";
    }
}
