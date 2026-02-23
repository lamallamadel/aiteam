package com.atlasia.ai.persistence;

import com.atlasia.ai.model.PasswordResetTokenEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenEntity, UUID> {
    
    Optional<PasswordResetTokenEntity> findByTokenHash(String tokenHash);
    
    @Modifying
    @Query("DELETE FROM PasswordResetTokenEntity prt WHERE prt.expiresAt < :now")
    int deleteExpiredTokens(Instant now);
    
    @Modifying
    @Query("UPDATE PasswordResetTokenEntity prt SET prt.used = true WHERE prt.userId = :userId AND prt.used = false")
    int invalidateAllUserTokens(UUID userId);
}
