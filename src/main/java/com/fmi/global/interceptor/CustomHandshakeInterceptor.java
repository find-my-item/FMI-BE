package com.fmi.global.interceptor;

import com.fmi.security.AuthCookieResolver;
import com.fmi.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final AuthCookieResolver authCookieResolver;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {

        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            log.debug("[WS-HS] not servlet request. class={}", request.getClass());
            return true;
        }

        HttpServletRequest httpRequest = servletRequest.getServletRequest();

        String token = authCookieResolver.findAccessToken(httpRequest).orElse(null);

        if (token == null) {
            log.warn(
                    "[WS-HS] handshake denied: access cookie missing. uri={}, origin={}",
                    httpRequest.getRequestURI(),
                    httpRequest.getHeader("Origin"));
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }

        if (!jwtTokenProvider.validateToken(token)) {
            log.warn(
                    "[WS-HS] handshake denied: invalid token. uri={}, origin={}",
                    httpRequest.getRequestURI(),
                    httpRequest.getHeader("Origin"));
            response.setStatusCode(org.springframework.http.HttpStatus.UNAUTHORIZED);
            return false;
        }

        Long userId = jwtTokenProvider.getUserId(token);
        String role = jwtTokenProvider.getRole(token);

        Authentication auth = new UsernamePasswordAuthenticationToken(
                String.valueOf(userId), null, List.of(new SimpleGrantedAuthority(role)));

        attributes.put("AUTH", auth);
        log.info("[WS-HS] handshake authenticated. userId={}, role={}", userId, role);

        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {}
}
