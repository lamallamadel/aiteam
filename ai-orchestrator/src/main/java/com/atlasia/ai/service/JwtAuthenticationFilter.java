package com.atlasia.ai.service;

import com.atlasia.ai.service.auth.BearerTokenAuthenticationResolver;
import org.springframework.security.core.Authentication;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final BearerTokenAuthenticationResolver bearerTokenResolver;

    public JwtAuthenticationFilter(BearerTokenAuthenticationResolver bearerTokenResolver) {
        this.bearerTokenResolver = bearerTokenResolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String bearerToken = extractJwtFromRequest(request);

        if (bearerToken != null) {
            try {
                Optional<Authentication> auth =
                        bearerTokenResolver.resolve(bearerToken);

                if (auth.isPresent() && SecurityContextHolder.getContext().getAuthentication() == null) {
                    SecurityContextHolder.getContext().setAuthentication(auth.get());
                    logger.debug("Set authentication for principal: {}", auth.get().getName());
                }
            } catch (Exception ex) {
                logger.error("Authentication check skipped due to error: {}", ex.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractJwtFromRequest(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);

        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }

        return null;
    }
}
