-- Conversation sessions: one per user+persona pair
CREATE TABLE conversation_session (
    id           UUID         PRIMARY KEY,
    session_key  VARCHAR(255) NOT NULL UNIQUE,
    persona_id   VARCHAR(100) NOT NULL,
    user_id      VARCHAR(100) NOT NULL,
    created_at   TIMESTAMP    NOT NULL,
    last_active  TIMESTAMP    NOT NULL
);

CREATE INDEX idx_conversation_session_key  ON conversation_session(session_key);
CREATE INDEX idx_conversation_session_user ON conversation_session(user_id);

-- Individual turns within a session
CREATE TABLE conversation_turn (
    id          UUID         PRIMARY KEY,
    session_id  UUID         NOT NULL REFERENCES conversation_session(id) ON DELETE CASCADE,
    role        VARCHAR(20)  NOT NULL,
    content     TEXT         NOT NULL,
    created_at  TIMESTAMP    NOT NULL
);

CREATE INDEX idx_conversation_turn_session ON conversation_turn(session_id, created_at DESC);
