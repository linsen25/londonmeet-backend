package com.londonmeet.server.service;

import com.londonmeet.common.exception.BusinessException;
import com.londonmeet.server.config.WechatProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Duration;
import java.util.Map;

/**
 * Client for WeChat code2Session login exchange.
 */
@Component
@RequiredArgsConstructor
public class WechatCode2SessionClient {

    private static final Logger log = LoggerFactory.getLogger(WechatCode2SessionClient.class);
    private static final String CODE2_SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    private final WechatProperties wechatProperties;
    private final ObjectMapper objectMapper;

    public WechatSession exchange(String code) {
        if (!StringUtils.hasText(code)) {
            throw new BusinessException("WeChat login code is required.");
        }

        if (!StringUtils.hasText(wechatProperties.getAppId()) || !StringUtils.hasText(wechatProperties.getSecret())) {
            throw new BusinessException("WeChat app-id and secret must be configured.");
        }

        String uri = UriComponentsBuilder.fromUriString(CODE2_SESSION_URL)
                .queryParam("appid", wechatProperties.getAppId())
                .queryParam("secret", wechatProperties.getSecret())
                .queryParam("js_code", code)
                .queryParam("grant_type", "authorization_code")
                .toUriString();

        String responseBody;
        try {
            responseBody = RestClient.builder()
                    .requestFactory(requestFactory())
                    .build()
                    .get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException ex) {
            log.error("Wechat code2Session HTTP call failed. code={}", maskCode(code), ex);
            throw new BusinessException("Failed to call WeChat code2Session.");
        }

        if (!StringUtils.hasText(responseBody)) {
            throw new BusinessException("Empty response from WeChat.");
        }

        Map<String, Object> response;
        try {
            response = objectMapper.readValue(responseBody, new TypeReference<>() {
            });
        } catch (JsonProcessingException ex) {
            log.error("Wechat code2Session returned invalid JSON. code={}, body={}", maskCode(code), responseBody, ex);
            throw new BusinessException("Invalid response from WeChat.");
        }

        Object errcode = response.get("errcode");
        if (errcode != null && !"0".equals(String.valueOf(errcode))) {
            Object errmsg = response.get("errmsg");
            log.warn("Wechat code2Session rejected code. errcode={}, errmsg={}, code={}", errcode, errmsg, maskCode(code));
            throw new BusinessException("WeChat login failed: " + (errmsg == null ? "unknown error" : errmsg));
        }

        Object openid = response.get("openid");
        if (openid == null || !StringUtils.hasText(String.valueOf(openid))) {
            throw new BusinessException("WeChat response did not include openid.");
        }

        return new WechatSession(
                String.valueOf(openid),
                response.get("unionid") == null ? null : String.valueOf(response.get("unionid")),
                response.get("session_key") == null ? null : String.valueOf(response.get("session_key"))
        );
    }

    private String maskCode(String code) {
        if (!StringUtils.hasText(code)) {
            return "";
        }
        String trimmed = code.trim();
        if (trimmed.length() <= 8) {
            return "****";
        }
        return trimmed.substring(0, 4) + "****" + trimmed.substring(trimmed.length() - 4);
    }

    private SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    @Data
    public static class WechatSession {
        private final String openid;
        private final String unionid;
        private final String sessionKey;
    }
}
