package com.londonmeet.server.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.londonmeet.common.response.ApiResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class DisabledUserRestrictionFilter extends OncePerRequestFilter {

    private static final String DISABLED_MESSAGE =
            "\u8d26\u53f7\u5df2\u88ab\u7981\u7528\uff0c\u5f53\u524d\u4ec5\u53ef\u6d4f\u89c8\u6d3b\u52a8\u3001\u67e5\u770b\u901a\u77e5\u548c\u63d0\u4ea4\u8d26\u53f7\u7533\u8bc9";

    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof LoginUser loginUser
                && "DISABLED".equals(loginUser.status())
                && !isAllowed(request)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setCharacterEncoding("UTF-8");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(
                    response.getWriter(),
                    ApiResponse.error(HttpServletResponse.SC_FORBIDDEN, DISABLED_MESSAGE)
            );
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isAllowed(HttpServletRequest request) {
        if ("GET".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return path.startsWith("/api/v1/appeals")
                || path.startsWith("/api/v1/notifications")
                || path.equals("/api/v1/activities/events");
    }
}
