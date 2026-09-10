package com.fmi.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AuthCookieResolver {

    private final String accessCookieName;
    private final String refreshCookieName;

    public AuthCookieResolver(
            @Value("${jwt.cookie.access-token-name:access_token}") String accessCookieName,
            @Value("${jwt.cookie.name:refresh_token}") String refreshCookieName) {
        this.accessCookieName = accessCookieName;
        this.refreshCookieName = refreshCookieName;
    }

    public Optional<String> findAccessToken(HttpServletRequest request) {
        return findValue(request, accessCookieName);
    }

    public Optional<String> findRefreshToken(HttpServletRequest request) {
        return findValue(request, refreshCookieName);
    }

    private Optional<String> findValue(HttpServletRequest request, String name) {
        if (request == null || request.getCookies() == null) {
            return Optional.empty();
        }

        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return Optional.ofNullable(cookie.getValue());
            }
        }
        return Optional.empty();
    }
}
