package com.fmi.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fmi.global.apiPayload.ApiResponse;
import com.fmi.global.apiPayload.code.status.ErrorStatus;
import com.fmi.security.AuthCookieResolver;
import com.fmi.security.CustomUserDetailsService;
import com.fmi.security.JwtAuthenticationFilter;
import com.fmi.security.JwtTokenProvider;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CorsProperties corsProperties;

    @Bean
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            JwtTokenProvider tokenProvider,
            CustomUserDetailsService userDetailsService,
            AuthCookieResolver authCookieResolver)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.dispatcherTypeMatchers(DispatcherType.ASYNC)
                        .permitAll()
                        .requestMatchers(
                                "/",
                                "/actuator/health",
                                "/actuator/info",
                                "/actuator/prometheus",
                                "/ws/**",
                                "/error",
                                "/health",
                                "/api/health")
                        .permitAll()
                        .requestMatchers("/auth/**", "/s3/**", "/chat-test.html", "/chat-test2.html", "/posts/filter")
                        .permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**")
                        .permitAll()
                        // 공지사항 - 조회만 공개, 좋아요/댓글 작성은 인증 필요
                        .requestMatchers(HttpMethod.GET, "/notices/**")
                        .permitAll()
                        .requestMatchers("/inquiries")
                        .permitAll()
                        .requestMatchers("/users/{userId}/page")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/users/{userId}/meta")
                        .permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/posts/search/**",
                                "/posts/*",
                                "/posts/*/translate",
                                "/posts/*/similar",
                                "/posts/*/share")
                        .permitAll()
                        // 댓글 목록/답글 조회 - 비회원도 조회 가능
                        .requestMatchers(HttpMethod.GET, "/comments/posts/*", "/comments/*/replies")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/main/posts/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/places")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/main/places/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/push/vapid-key")
                        .permitAll()
                        // 관리자 전용 API 보호
                        .requestMatchers("/admin/**")
                        .hasRole("ADMIN")
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint(customAuthenticationEntryPoint()))
                .httpBasic(h -> h.disable());

        http.addFilterBefore(
                new JwtAuthenticationFilter(tokenProvider, userDetailsService, authCookieResolver),
                UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 인증 실패 시 프로젝트 표준 ApiResponse 형식으로 응답
     */
    @Bean
    public AuthenticationEntryPoint customAuthenticationEntryPoint() {
        return new AuthenticationEntryPoint() {
            private final ObjectMapper objectMapper = new ObjectMapper();

            @Override
            public void commence(
                    HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
                    throws IOException {
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");

                ApiResponse<Object> apiResponse = ApiResponse.onFailure(
                        ErrorStatus._UNAUTHORIZED.getReasonHttpStatus().getCode(),
                        ErrorStatus._UNAUTHORIZED.getReasonHttpStatus().getMessage(),
                        null);

                objectMapper.writeValue(response.getWriter(), apiResponse);
            }
        };
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowCredentials(true);
        config.setAllowedOriginPatterns(corsProperties.getAllowedOriginPatterns());
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.setMaxAge(3600L);
        config.setExposedHeaders(Arrays.asList("Authorization"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
