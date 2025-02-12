package com.mercury.star_be.global.config;

import com.mercury.star_be.global.error.GlobalExceptionHandler;
import com.mercury.star_be.user.Handler.CustomFailedHandler;
import com.mercury.star_be.user.Handler.CustomSuccessHandler;
import com.mercury.star_be.user.service.UserServiceImpl;
import com.mercury.star_be.user.util.JwtFilter;
import com.mercury.star_be.user.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@RequiredArgsConstructor
public class SecurityConfig{

    private final UserServiceImpl customOAuth2UserService;
    private final CustomSuccessHandler customSuccessHandler;
    private final GlobalExceptionHandler globalExceptionHandler;
    private final CustomFailedHandler customFailedHandler;
    private final JwtUtil jwtUtil;


    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(auth -> auth.disable())       // CSRF 방어 기능 비활성화. front와의 연결은 WebConfig에 설정
                .headers(x -> x.frameOptions(y -> y.disable()))     // H2-console
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(
                                "/api/**" // front단에서의 요청
                                , "/docs/index.html", // rest docs 요청
                                "/chat/**" // chat 웹소켓
                                ,"/timer/**", "favion,ico" // timer 웹소켓
                                ,"/", "/login/oauth2/code/**", "/oauth2-jwt-header", "/oauth2Login", "/api/check-auth", "/oauth2/callback",
                                "/api/auth/reissue", "/api/groups/**", "/groups/**", "/error","/reissue",
                                "/groups",
                                "/fileupload/**",
                                "/api/timers/ranking/**",
                                "/api/chats/**"
                        ).permitAll() //기본 permiAll로 셋팅. 추후 변경 필요
                        .anyRequest().authenticated()  // 위 경로 말고 다른 경로들은 전부 인증필요
                );


        // CORS 설정
        http.cors(corsCustomizer -> corsCustomizer.configurationSource(new CorsConfigurationSource() {
            @Override
            public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
                CorsConfiguration configuration = new CorsConfiguration();

                configuration.setAllowedOrigins(Arrays.asList(
                    // "http://34.22.66.212:3001",
                    // "http://34.22.98.26:8080",
                    // "https://34.22.66.212:3001",
                    // "https://34.22.98.26:8080",
                    "http://localhost:5173",
                    "https://mercurystudy.store"));
                configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
                configuration.setAllowedOriginPatterns(Arrays.asList("http://localhost:5173", "https://mercurystudy.store"));
                configuration.setAllowedHeaders(List.of("*"));
                configuration.setAllowCredentials(true);
                // configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Accept"));
                configuration.setMaxAge(3600L);
                configuration.setExposedHeaders(Arrays.asList("Authorization", "Set-Cookie","Content-Type","userid"));
                return configuration;
            }
        }));


        // 로그인 폼 비활성화
        http.formLogin(auth -> auth.disable());
        http.httpBasic(auth -> auth.disable());

        // 필터 설정
        http.addFilterAfter(new JwtFilter(jwtUtil), UsernamePasswordAuthenticationFilter.class); // JWT 필터

        // OAuth2 설정
        http.oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfoEndpointConfig -> userInfoEndpointConfig
                        .userService(customOAuth2UserService)) // 사용자 정보 처리 서비스
                .successHandler(customSuccessHandler)
                .failureHandler((customFailedHandler)));


        // 인증되지 않은 사용자 리다이렉트 설정
        http.exceptionHandling(exception ->
                exception.authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                }));

        // 세션 관리
        http.sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        return http.build();
    }
}
