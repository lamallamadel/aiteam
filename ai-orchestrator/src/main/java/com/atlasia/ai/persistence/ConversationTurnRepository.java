package com.atlasia.ai.persistence;

import com.atlasia.ai.model.ConversationTurnEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ConversationTurnRepository extends JpaRepository<ConversationTurnEntity, UUID> {

    /**
     * Returns the last N turns for a session, re-ordered oldest-first for prompt injection.
     * The subquery selects newest N by createdAt DESC (via Pageable), then the outer
     * query re-orders ASC so they read chronologically in the prompt.
     */
    @Query("""
        SELECT t FROM ConversationTurnEntity t
        WHERE t.session.id = :sessionId
          AND t.id IN (
            SELECT t2.id FROM ConversationTurnEntity t2
            WHERE t2.session.id = :sessionId
            ORDER BY t2.createdAt DESC
          )
        ORDER BY t.createdAt ASC
        """)
    List<ConversationTurnEntity> findLastNBySessionId(
            @Param("sessionId") UUID sessionId,
            Pageable pageable
    );
}
