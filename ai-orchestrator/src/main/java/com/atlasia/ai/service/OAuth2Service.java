package com.atlasia.ai.service;

import com.atlasia.ai.model.OAuth2AccountEntity;
import com.atlasia.ai.model.UserEntity;
import com.atlasia.ai.persistence.OAuth2AccountRepository;
import com.atlasia.ai.persistence.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class OAuth2Service {

    private static final Logger logger = LoggerFactory.getLogger(OAuth2Service.class);
    
    private final OAuth2AccountRepository oauth2AccountRepository;
    private final UserRepository userRepository;

    public OAuth2Service(OAuth2AccountRepository oauth2AccountRepository, UserRepository userRepository) {
        this.oauth2AccountRepository = oauth2AccountRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public OAuth2AccountEntity linkOAuth2Account(UUID userId, String provider, String providerUserId, 
                                                   String accessToken, String refreshToken) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Optional<OAuth2AccountEntity> existingAccount = oauth2AccountRepository
                .findByProviderAndProviderUserId(provider, providerUserId);
        
        if (existingAccount.isPresent()) {
            if (!existingAccount.get().getUser().getId().equals(userId)) {
                throw new IllegalStateException("OAuth2 account is already linked to another user");
            }
            OAuth2AccountEntity account = existingAccount.get();
            account.setAccessToken(accessToken);
            account.setRefreshToken(refreshToken);
            logger.info("Updated OAuth2 account link: userId={}, provider={}", userId, provider);
            return oauth2AccountRepository.save(account);
        }

        OAuth2AccountEntity newAccount = new OAuth2AccountEntity(user, provider, providerUserId, accessToken, refreshToken);
        OAuth2AccountEntity savedAccount = oauth2AccountRepository.save(newAccount);
        logger.info("Linked new OAuth2 account: userId={}, provider={}", userId, provider);
        return savedAccount;
    }

    @Transactional(readOnly = true)
    public Optional<OAuth2AccountEntity> findByProviderAndUserId(String provider, String providerUserId) {
        return oauth2AccountRepository.findByProviderAndProviderUserId(provider, providerUserId);
    }

    @Transactional(readOnly = true)
    public boolean isAccountLinked(UUID userId, String provider) {
        return oauth2AccountRepository.findByUserId(userId).stream()
                .anyMatch(account -> account.getProvider().equals(provider));
    }
}
