package com.atlasia.ai.config;

import com.atlasia.ai.model.OAuth2AccountEntity;
import com.atlasia.ai.model.UserEntity;
import com.atlasia.ai.persistence.OAuth2AccountRepository;
import com.atlasia.ai.persistence.UserRepository;
import com.atlasia.ai.service.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.UUID;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2LoginSuccessHandler.class);
    
    private final UserRepository userRepository;
    private final OAuth2AccountRepository oauth2AccountRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    
    @Value("${atlasia.oauth2.frontend-callback-url:http://localhost:4200/auth/callback}")
    private String frontendCallbackUrl;

    public OAuth2LoginSuccessHandler(
            UserRepository userRepository,
            OAuth2AccountRepository oauth2AccountRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.oauth2AccountRepository = oauth2AccountRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        if (!(authentication instanceof OAuth2AuthenticationToken)) {
            logger.warn("Authentication is not OAuth2AuthenticationToken");
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid authentication type");
            return;
        }

        OAuth2AuthenticationToken oauth2Token = (OAuth2AuthenticationToken) authentication;
        OAuth2User oauth2User = oauth2Token.getPrincipal();
        String provider = oauth2Token.getAuthorizedClientRegistrationId();
        
        String providerUserId = extractProviderUserId(oauth2User, provider);
        String email = extractEmail(oauth2User, provider);
        String name = extractName(oauth2User, provider);
        
        if (providerUserId == null || email == null) {
            logger.error("Failed to extract required OAuth2 user info from provider: {}", provider);
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Invalid OAuth2 user data");
            return;
        }

        try {
            UserEntity user = oauth2AccountRepository
                    .findByProviderAndProviderUserId(provider, providerUserId)
                    .map(OAuth2AccountEntity::getUser)
                    .orElseGet(() -> createUserFromOAuth2(email, name, provider, providerUserId));

            String accessToken = jwtService.generateAccessToken(user);
            String refreshToken = jwtService.generateRefreshToken(user);

            String redirectUrl = UriComponentsBuilder.fromUriString(frontendCallbackUrl)
                    .queryParam("token", accessToken)
                    .queryParam("refreshToken", refreshToken)
                    .build()
                    .toUriString();

            logger.info("OAuth2 login successful for user: {} via provider: {}", email, provider);
            response.sendRedirect(redirectUrl);

        } catch (Exception e) {
            logger.error("Failed to process OAuth2 login", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Authentication failed");
        }
    }

    private UserEntity createUserFromOAuth2(String email, String name, String provider, String providerUserId) {
        UserEntity user = userRepository.findByEmail(email).orElseGet(() -> {
            String username = generateUniqueUsername(email, name);
            String randomPassword = passwordEncoder.encode(UUID.randomUUID().toString());
            UserEntity newUser = new UserEntity(username, email, randomPassword);
            newUser = userRepository.save(newUser);
            logger.info("Created new user from OAuth2: {} via provider: {}", email, provider);
            return newUser;
        });

        OAuth2AccountEntity oauth2Account = new OAuth2AccountEntity(user, provider, providerUserId, null, null);
        oauth2AccountRepository.save(oauth2Account);
        logger.info("Linked OAuth2 account: provider={}, providerUserId={}", provider, providerUserId);

        return user;
    }

    private String generateUniqueUsername(String email, String name) {
        String baseUsername = name != null && !name.isEmpty() 
                ? name.replaceAll("\\s+", "").toLowerCase()
                : email.split("@")[0];
        
        String username = baseUsername;
        int counter = 1;
        while (userRepository.existsByUsername(username)) {
            username = baseUsername + counter++;
        }
        return username;
    }

    private String extractProviderUserId(OAuth2User oauth2User, String provider) {
        return switch (provider.toLowerCase()) {
            case "github" -> oauth2User.getAttribute("id") != null ? 
                    oauth2User.getAttribute("id").toString() : null;
            case "google" -> oauth2User.getAttribute("sub");
            case "gitlab" -> oauth2User.getAttribute("id") != null ? 
                    oauth2User.getAttribute("id").toString() : null;
            default -> oauth2User.getName();
        };
    }

    private String extractEmail(OAuth2User oauth2User, String provider) {
        return oauth2User.getAttribute("email");
    }

    private String extractName(OAuth2User oauth2User, String provider) {
        return switch (provider.toLowerCase()) {
            case "github" -> {
                String name = oauth2User.getAttribute("name");
                if (name == null || name.isEmpty()) {
                    name = oauth2User.getAttribute("login");
                }
                yield name;
            }
            case "google" -> oauth2User.getAttribute("name");
            case "gitlab" -> {
                String name = oauth2User.getAttribute("name");
                if (name == null || name.isEmpty()) {
                    name = oauth2User.getAttribute("username");
                }
                yield name;
            }
            default -> oauth2User.getAttribute("name");
        };
    }
}
