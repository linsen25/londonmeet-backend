package com.londonmeet.server.security;

import com.londonmeet.server.exception.TooManyLoginAttemptsException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AdminLoginAttemptLimiter {

    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(10);
    private static final Duration LOCK_DURATION = Duration.ofMinutes(10);

    private final Map<String, AttemptState> attempts = new ConcurrentHashMap<>();

    public void checkAllowed(String username, String clientIp) {
        AttemptState state = attempts.get(key(username, clientIp));
        if (state == null || state.lockedUntil == null) {
            return;
        }
        if (Instant.now().isBefore(state.lockedUntil)) {
            throw new TooManyLoginAttemptsException("登录失败次数过多，请稍后再试");
        }
        attempts.remove(key(username, clientIp));
    }

    public void recordSuccess(String username, String clientIp) {
        attempts.remove(key(username, clientIp));
    }

    public void recordFailure(String username, String clientIp) {
        String key = key(username, clientIp);
        Instant now = Instant.now();
        attempts.compute(key, (ignored, state) -> {
            if (state == null || now.isAfter(state.windowStarted.plus(WINDOW))) {
                state = new AttemptState();
                state.windowStarted = now;
            }
            state.failures++;
            if (state.failures >= MAX_FAILURES) {
                state.lockedUntil = now.plus(LOCK_DURATION);
            }
            return state;
        });
    }

    private String key(String username, String clientIp) {
        String normalizedUsername = StringUtils.hasText(username) ? username.trim().toLowerCase() : "unknown";
        String normalizedIp = StringUtils.hasText(clientIp) ? clientIp.trim() : "unknown";
        return normalizedUsername + "@" + normalizedIp;
    }

    private static class AttemptState {
        private Instant windowStarted;
        private int failures;
        private Instant lockedUntil;
    }
}
