package com.londonmeet.server.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.londonmeet.common.constant.JwtClaimsConstant;
import com.londonmeet.common.constant.MessageConstant;
import com.londonmeet.common.properties.JwtProperties;
import com.londonmeet.common.response.ApiResponse;
import com.londonmeet.common.utils.JwtUtil;
import com.londonmeet.pojo.entity.User;
import com.londonmeet.server.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT authentication filter for protected API requests.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String USER_STATUS_ACTIVE = "ACTIVE";

    private final JwtProperties jwtProperties;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String token = authorization.substring(BEARER_PREFIX.length()).trim();
            Claims claims = JwtUtil.parseToken(jwtProperties.getSecretKey(), token);
            Long userId = parseUserId(claims.get(JwtClaimsConstant.USER_ID));
            String openid = claims.get(JwtClaimsConstant.OPENID, String.class);

            User user = userRepository.findById(userId)
                    .filter(item -> USER_STATUS_ACTIVE.equals(item.getStatus()))
                    .orElseThrow(() -> new JwtException(MessageConstant.UNAUTHORIZED));

            LoginUser loginUser = new LoginUser(user.getId(), openid, user.getRole());
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    loginUser,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()))
            );

            SecurityContextHolder.getContext().setAuthentication(authentication);
            request.setAttribute(JwtRequestAttributes.LOGIN_USER, loginUser);
            request.setAttribute(JwtRequestAttributes.USER_ID, user.getId());
            request.setAttribute(JwtRequestAttributes.OPENID, openid);

            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException ex) {
            SecurityContextHolder.clearContext();
            writeUnauthorized(response);
        }
    }

    private Long parseUserId(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            return Long.parseLong(text);
        }
        throw new IllegalArgumentException("Invalid userId claim.");
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ApiResponse.error(MessageConstant.UNAUTHORIZED));
    }
}
