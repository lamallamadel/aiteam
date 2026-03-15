package com.atlasia.ai.persistence;

import com.atlasia.ai.model.ChatArtifactEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatArtifactRepository extends JpaRepository<ChatArtifactEntity, UUID> {

    /** All latest artifacts for a session — shown as the "current state" of generated code. */
    List<ChatArtifactEntity> findBySessionKeyAndIsLatestTrueOrderByCreatedAtDesc(String sessionKey);

    /** Latest version of a specific file path in a session. */
    Optional<ChatArtifactEntity> findBySessionKeyAndFilePathAndIsLatestTrue(
            String sessionKey, String filePath);

    /** Full version history for a file path. */
    List<ChatArtifactEntity> findBySessionKeyAndFilePathOrderByCreatedAtDesc(
            String sessionKey, String filePath);

    /** Mark all previous versions of a file as not-latest before inserting a new one. */
    @Modifying
    @Query("UPDATE ChatArtifactEntity a SET a.isLatest = false " +
           "WHERE a.sessionKey = :sessionKey AND a.filePath = :filePath AND a.isLatest = true")
    void markPreviousVersionsAsNotLatest(@Param("sessionKey") String sessionKey,
                                          @Param("filePath") String filePath);
}
