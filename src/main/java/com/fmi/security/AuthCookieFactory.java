package com.fmi.security;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class AuthCookieFactory {

    private final boolean secure;
    private final String sameSite;
    private final String parentDomain;
    private final String accessCookieName;
    private final String refreshCookieName;
    private final Clock clock;

    public AuthCookieFactory(
            @Value("${jwt.cookie.secure:false}") boolean secure,
            @Value("${jwt.cookie.same-site:Lax}") String sameSite,
            @Value("${jwt.cookie.parent-domain:}") String parentDomain,
            @Value("${jwt.cookie.access-token-name:access_token}") String accessCookieName,
            @Value("${jwt.cookie.name:refresh_token}") String refreshCookieName,
            Clock clock) {
        this.secure = secure;
        this.sameSite = sameSite;
        this.parentDomain = parentDomain;
        this.accessCookieName = accessCookieName;
        this.refreshCookieName = refreshCookieName;
        this.clock = clock;
    }

    public ResponseCookie createAccessCookie(HttpServletRequest request, String token, Date expiration) {
        return createCookie(
                request, accessCookieName, token, Duration.between(clock.instant(), expiration.toInstant()));
    }

    public ResponseCookie createRefreshCookie(HttpServletRequest request, String token, Date expiration) {
        return createCookie(
                request, refreshCookieName, token, Duration.between(clock.instant(), expiration.toInstant()));
    }

    public ResponseCookie expireAccessCookie(HttpServletRequest request) {
        return createCookie(request, accessCookieName, "", Duration.ZERO);
    }

    public ResponseCookie expireRefreshCookie(HttpServletRequest request) {
        return createCookie(request, refreshCookieName, "", Duration.ZERO);
    }

    private ResponseCookie createCookie(HttpServletRequest request, String name, String value, Duration maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/")
                .maxAge(maxAge);

        String domain = resolveDomain(request);
        if (domain != null) {
            builder.domain(domain);
        }
        return builder.build();
    }

    private String resolveDomain(HttpServletRequest request) {
        if (parentDomain == null || parentDomain.isBlank() || request == null) {
            return null;
        }

        String host = hostOf(request.getHeader("Origin"));
        if (host == null) {
            host = hostOf(request.getHeader("Referer"));
        }
        if (host == null) {
            return null;
        }

        String bare = parentDomain.startsWith(".") ? parentDomain.substring(1) : parentDomain;
        boolean belongsToParent = host.equals(bare) || host.endsWith("." + bare);
        return belongsToParent ? parentDomain : null;
    }

    private String hostOf(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        try {
            return URI.create(url).getHost();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
