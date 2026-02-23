package com.atlasia.ai.persistence;

import com.atlasia.ai.model.OAuth2AccountEntity;
import com.atlasia.ai.model.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OAuth2AccountRepository extends JpaRepository<OAuth2AccountEntity, Long> {
    
    Optional<OAuth2AccountEntity> findByProviderAndProviderUserId(String provider, String providerUserId);
    
    List<OAuth2AccountEntity> findByUser(UserEntity user);
    
    @Query("SELECT o FROM OAuth2AccountEntity o WHERE o.user.id = :userId")
    List<OAuth2AccountEntity> findByUserId(@Param("userId") UUID userId);
    
    boolean existsByProviderAndProviderUserId(String provider, String providerUserId);
    
    Optional<OAuth2AccountEntity> findByUserAndProvider(UserEntity user, String provider);
}
