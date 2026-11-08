package com.smartmart.auth.web;

import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.withDefaults;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Refuses a login attempt with 429 and a {@code Retry-After} header (in whole
 * seconds) while {@link FailedLoginLimiter} says the username and address must
 * wait (FR-AUTH-8). The refused attempt is not checked against the password.
 */
class LoginLimitFilter extends OncePerRequestFilter {

    private static final RequestMatcher LOGIN = withDefaults().matcher(HttpMethod.POST, "/login");

    /** Request attribute holding the wait in whole seconds, for the 429 page. */
    static final String RETRY_AFTER_ATTRIBUTE = "retryAfterSeconds";

    private final FailedLoginLimiter limiter;

    LoginLimitFilter(FailedLoginLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !LOGIN.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<Duration> wait = limiter.waitBeforeNextAttempt(username(request), request.getRemoteAddr());
        if (wait.isPresent()) {
            long seconds = Math.max(1, (wait.get().toMillis() + 999) / 1000);
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
            // The 429 page shows the same wait (templates/error/429.html, SCRUM-56).
            request.setAttribute(RETRY_AFTER_ATTRIBUTE, seconds);
            response.sendError(HttpStatus.TOO_MANY_REQUESTS.value());
            return;
        }
        chain.doFilter(request, response);
    }

    static String username(HttpServletRequest request) {
        String username = request.getParameter("username");
        return username == null ? "" : username;
    }
}
