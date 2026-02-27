package com.atlasia.ai.config;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitingFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitingFilter.class);

    private final RateLimiterRegistry rateLimiterRegistry;
    private final MeterRegistry meterRegistry;
    private final Map<String, RateLimiter> ipRateLimiters = new ConcurrentHashMap<>();
    private final Map<String, RateLimiter> userRateLimiters = new ConcurrentHashMap<>();
    private final Counter authRejectedCounter;
    private final Counter apiRejectedCounter;
    private final Counter uploadRejectedCounter;

    public RateLimitingFilter(RateLimiterRegistry rateLimiterRegistry, MeterRegistry meterRegistry) {
        this.rateLimiterRegistry = rateLimiterRegistry;
        this.meterRegistry = meterRegistry;
        this.authRejectedCounter = Counter.builder("ratelimiter.rejected.auth")
                .description("Number of rejected authentication requests")
                .register(meterRegistry);
        this.apiRejectedCounter = Counter.builder("ratelimiter.rejected.api")
                .description("Number of rejected API requests")
                .register(meterRegistry);
        this.uploadRejectedCounter = Counter.builder("ratelimiter.rejected.upload")
                .description("Number of rejected upload requests")
                .register(meterRegistry);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        String path = httpRequest.getRequestURI();
        String method = httpRequest.getMethod();
        
                try {
                    if (isUploadEndpoint(path)) {
                        String userId = getCurrentUserId();
                        if (userId != null) {
                            RateLimiter rateLimiter = getUserRateLimiter(userId, "upload");
                            
                            rateLimiter.executeRunnable(() -> {});
                            addRateLimitHeaders(httpResponse, rateLimiter);
                        }
                        
                    } else if (isApiEndpoint(path)) {                String userId = getCurrentUserId();
                if (userId != null) {
                    RateLimiter rateLimiter = getUserRateLimiter(userId, "api");
                    
                    rateLimiter.executeRunnable(() -> {});
                    addRateLimitHeaders(httpResponse, rateLimiter);
                }
            }
            
            chain.doFilter(request, response);
            
        } catch (RequestNotPermitted e) {
            logger.warn("Rate limit exceeded for path: {} from {}", path, getClientIp(httpRequest));
            
            if (isAuthEndpoint(path)) {
                authRejectedCounter.increment();
            } else if (isUploadEndpoint(path)) {
                uploadRejectedCounter.increment();
            } else {
                apiRejectedCounter.increment();
            }
            
            httpResponse.setStatus(429);
            httpResponse.setContentType("application/json");
            httpResponse.setHeader("X-RateLimit-Remaining", "0");
            httpResponse.setHeader("Retry-After", "60");
            httpResponse.getWriter().write(
                "{\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Please try again later.\"}"
            );
        }
    }

    private boolean isAuthEndpoint(String path) {
        return path.startsWith("/api/auth/login") || 
               path.startsWith("/api/auth/register") ||
               path.startsWith("/api/auth/password-reset");
    }

    private boolean isUploadEndpoint(String path) {
        return path.contains("/upload");
    }

    private boolean isApiEndpoint(String path) {
        return path.startsWith("/api/") && !isAuthEndpoint(path) && !isUploadEndpoint(path);
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }

    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && 
            !"anonymousUser".equals(authentication.getPrincipal())) {
            return authentication.getName();
        }
        return null;
    }

    private RateLimiter getIpRateLimiter(String ipAddress, String configName) {
        String key = configName + ":" + ipAddress;
        return ipRateLimiters.computeIfAbsent(key, k -> {
            RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(k, configName);
            registerMetrics(rateLimiter, configName, ipAddress);
            return rateLimiter;
        });
    }

    private RateLimiter getUserRateLimiter(String userId, String configName) {
        String key = configName + ":" + userId;
        return userRateLimiters.computeIfAbsent(key, k -> {
            RateLimiter rateLimiter = rateLimiterRegistry.rateLimiter(k, configName);
            registerMetrics(rateLimiter, configName, userId);
            return rateLimiter;
        });
    }

    private void registerMetrics(RateLimiter rateLimiter, String configName, String identifier) {
        meterRegistry.gauge("ratelimiter.available.permissions",
                rateLimiter,
                r -> r.getMetrics().getAvailablePermissions());
        
        meterRegistry.gauge("ratelimiter.waiting.threads",
                rateLimiter,
                r -> r.getMetrics().getNumberOfWaitingThreads());
    }

    private void addRateLimitHeaders(HttpServletResponse response, RateLimiter rateLimiter) {
        int availablePermissions = rateLimiter.getMetrics().getAvailablePermissions();
        response.setHeader("X-RateLimit-Remaining", String.valueOf(availablePermissions));
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        logger.info("RateLimitingFilter initialized");
    }

    @Override
    public void destroy() {
        logger.info("RateLimitingFilter destroyed");
    }
}
