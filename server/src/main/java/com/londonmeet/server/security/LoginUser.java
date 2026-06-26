package com.londonmeet.server.security;

/**
 * Authenticated user stored in Spring Security context and request attributes.
 */
public record LoginUser(Long userId, String openid, String role, String status) {
}
