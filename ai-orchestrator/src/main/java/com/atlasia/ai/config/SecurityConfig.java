package com.atlasia.ai.config;

import com.atlasia.ai.service.JwtAuthenticationFilter;
import com.atlasia.ai.service.observability.CorrelationIdHolder;
import io.opentelemetry.api.trace.Span;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableScheduling
@EnableConfigurationProperties({ CorsProperties.class, OAuth2Properties.class })
public class SecurityConfig {

        private final JwtAuthenticationFilter jwtAuthenticationFilter;
        private final RateLimitingFilter rateLimitingFilter;
        private final CorsProperties corsProperties;
        private final OAuth2LoginSuccessHandler oauth2LoginSuccessHandler;
        private final CorrelationIdFilter correlationIdFilter;

        public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                        RateLimitingFilter rateLimitingFilter,
                        CorsProperties corsProperties,
                        @Lazy OAuth2LoginSuccessHandler oauth2LoginSuccessHandler) {
                this.jwtAuthenticationFilter = jwtAuthenticationFilter;
                this.rateLimitingFilter = rateLimitingFilter;
                this.corsProperties = corsProperties;
                this.oauth2LoginSuccessHandler = oauth2LoginSuccessHandler;
                this.correlationIdFilter = new CorrelationIdFilter();
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                CookieCsrfTokenRepository csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
                csrfTokenRepository.setCookieName("XSRF-TOKEN");
                csrfTokenRepository.setHeaderName("X-CSRF-TOKEN");

                CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
                requestHandler.setCsrfRequestAttributeName("_csrf");

                http
                                .csrf(csrf -> csrf
                                                .csrfTokenRepository(csrfTokenRepository)
                                                .csrfTokenRequestHandler(requestHandler)
                                                .ignoringRequestMatchers(
                                                                "/api/auth/login",
                                                                "/api/auth/register",
                                                                "/api/auth/refresh",
                                                                "/api/auth/logout",
                                                                "/api/auth/csrf",
                                                                "/api/auth/me",
                                                                "/api/auth/mfa/**",
                                                                "/api/runs/**",
                                                                "/api/a2a/**",
                                                                "/api/webhooks/**",
                                                                "/actuator/**",
                                                                "/ws/**"))
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .exceptionHandling(exception -> exception
                                                .authenticationEntryPoint((request, response, authException) -> {
                                                        response.setStatus(401);
                                                        response.setContentType("application/json");
                                                        response.getWriter().write(
                                                                        "{\"error\":\"Unauthorized\",\"message\":\""
                                                                                        + authException.getMessage()
                                                                                        + "\"}");
                                                })
                                                .accessDeniedHandler((request, response, accessDeniedException) -> {
                                                        response.setStatus(403);
                                                        response.setContentType("application/json");
                                                        response.getWriter()
                                                                        .write("{\"error\":\"Forbidden\",\"message\":\""
                                                                                        + accessDeniedException
                                                                                                        .getMessage()
                                                                                        + "\"}");
                                                }))
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers("/api/auth/**").permitAll()
                                                .requestMatchers("/oauth2/**").permitAll()
                                                .requestMatchers("/login/oauth2/**").permitAll()
                                                .requestMatchers("/actuator/health").permitAll()
                                                .requestMatchers("/actuator/prometheus").permitAll()
                                                .requestMatchers("/actuator/metrics/**").permitAll()
                                                .requestMatchers("/ws/**").permitAll()
                                                .requestMatchers("/api/a2a/**").permitAll()
                                                .requestMatchers("/api/webhooks/**").permitAll()
                                                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                                                .requestMatchers("/api/**").authenticated()
                                                .anyRequest().authenticated())
                                .oauth2Login(oauth2 -> oauth2
                                                .successHandler(oauth2LoginSuccessHandler))
                                .addFilterBefore(correlationIdFilter, RateLimitingFilter.class)
                                .addFilterBefore(rateLimitingFilter, UsernamePasswordAuthenticationFilter.class)
                                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration configuration = new CorsConfiguration();
                configuration.setAllowedOrigins(corsProperties.getAllowedOrigins());
                configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
                configuration.setAllowedHeaders(Arrays.asList("*"));
                configuration.setExposedHeaders(Arrays.asList("X-CSRF-TOKEN", "X-RateLimit-Remaining", "Retry-After"));
                configuration.setAllowCredentials(true);
                configuration.setMaxAge(3600L);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);
                return source;
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder(12);
        }

        public static class CorrelationIdFilter extends OncePerRequestFilter {
                private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

                @Override
                protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                FilterChain filterChain) throws ServletException, IOException {
                        String correlationId = request.getHeader(CORRELATION_ID_HEADER);

                        if (!StringUtils.hasText(correlationId)) {
                                correlationId = CorrelationIdHolder.getCorrelationId();
                                if (!StringUtils.hasText(correlationId)) {
                                        correlationId = CorrelationIdHolder.generateCorrelationId();
                                }
                        }

                        CorrelationIdHolder.setCorrelationId(correlationId);
                        response.setHeader(CORRELATION_ID_HEADER, correlationId);

                        Span currentSpan = Span.current();
                        if (currentSpan != null) {
                                currentSpan.setAttribute("correlation.id", correlationId);
                        }

                        try {
                                filterChain.doFilter(request, response);
                        } finally {
                                CorrelationIdHolder.clear();
                        }
                }
        }
}
