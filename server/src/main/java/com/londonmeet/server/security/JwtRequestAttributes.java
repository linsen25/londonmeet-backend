package com.londonmeet.server.security;

/**
 * Request attribute names written by JWT authentication.
 */
public final class JwtRequestAttributes {

    public static final String LOGIN_USER = "loginUser";
    public static final String USER_ID = "userId";
    public static final String OPENID = "openid";

    private JwtRequestAttributes() {
    }
}
