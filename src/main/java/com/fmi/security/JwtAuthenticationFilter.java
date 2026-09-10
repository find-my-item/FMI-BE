package com.fmi.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fmi.global.apiPayload.ApiResponse;
import com.fmi.global.apiPayload.code.ErrorReasonDTO;
import com.fmi.global.apiPayload.code.status.ErrorStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final UserDetailsService userDetailsService;
    private final AuthCookieResolver authCookieResolver;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(
            JwtTokenProvider tokenProvider,
            UserDetailsService userDetailsService,
            AuthCookieResolver authCookieResolver) {
        this.tokenProvider = tokenProvider;
        this.userDetailsService = userDetailsService;
        this.authCookieResolver = authCookieResolver;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String token = authCookieResolver.findAccessToken(request).orElse(null);

        // 2. 쿠키에 없으면 Authorization 헤더에서 읽기 (하위 호환성)
        if (!StringUtils.hasText(token)) {
            String bearer = request.getHeader("Authorization");
            if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
                token = bearer.substring(7);
            }
        }

        if (StringUtils.hasText(token) && tokenProvider.validateToken(token)) {
            try {
                String username = tokenProvider.getSubject(token);
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (UsernameNotFoundException e) {
                // 필터에서 발생한 UsernameNotFoundException을 일관된 형식으로 처리
                handleAuthenticationException(response, e);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void handleAuthenticationException(HttpServletResponse response, UsernameNotFoundException e)
            throws IOException {
        ErrorReasonDTO errorReason;

        // GeneralException이 원인인 경우 해당 에러 정보 사용
        if (e.getCause() instanceof com.fmi.global.apiPayload.exception.GeneralException) {
            com.fmi.global.apiPayload.exception.GeneralException generalException =
                    (com.fmi.global.apiPayload.exception.GeneralException) e.getCause();
            errorReason = generalException.getErrorReasonHttpStatus();
        } else {
            // 일반 UsernameNotFoundException인 경우 _USER_NOT_FOUND 사용
            errorReason = ErrorStatus._USER_NOT_FOUND.getReasonHttpStatus();
        }

        response.setStatus(errorReason.getHttpStatus().value());
        response.setContentType("application/json;charset=UTF-8");

        ApiResponse<Object> apiResponse = ApiResponse.onFailure(errorReason.getCode(), errorReason.getMessage(), null);

        objectMapper.writeValue(response.getWriter(), apiResponse);
    }
}
