package com.atlasia.ai.e2e.scenarios;

import com.atlasia.ai.e2e.configuration.AbstractE2ETest;
import com.atlasia.ai.model.CollaborationEventEntity;
import com.atlasia.ai.model.PersistedCollaborationMessage;
import com.atlasia.ai.model.RoleEntity;
import com.atlasia.ai.model.RunEntity;
import com.atlasia.ai.model.RunStatus;
import com.atlasia.ai.model.UserEntity;
import com.atlasia.ai.persistence.CollaborationEventRepository;
import com.atlasia.ai.persistence.PersistedCollaborationMessageRepository;
import com.atlasia.ai.persistence.RoleRepository;
import com.atlasia.ai.persistence.RunRepository;
import com.atlasia.ai.persistence.UserRepository;
import com.atlasia.ai.service.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

public class WebSocketCollaborationE2ETest extends AbstractE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RunRepository runRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CollaborationEventRepository eventRepository;

    @Autowired
    private PersistedCollaborationMessageRepository messageRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private UserEntity testUser1;
    private UserEntity testUser2;
    private RunEntity testRun;
    private String wsUrl;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM collaboration_events;");
        jdbcTemplate.execute("DELETE FROM persisted_collaboration_messages;");
        jdbcTemplate.execute("DELETE FROM ai_trace_event;");
        jdbcTemplate.execute("DELETE FROM ai_run_artifact;");
        jdbcTemplate.execute("DELETE FROM ai_run;");
        jdbcTemplate.execute("DELETE FROM user_roles;");
        jdbcTemplate.execute("DELETE FROM users;");
        jdbcTemplate.execute("DELETE FROM roles;");

        RoleEntity userRole = new RoleEntity("USER", "Standard user");
        roleRepository.save(userRole);

        testUser1 = new UserEntity("user1", "user1@example.com",
                passwordEncoder.encode("Password123!"));
        testUser1.setEnabled(true);
        testUser1.setRoles(Set.of(userRole));
        testUser1 = userRepository.save(testUser1);

        testUser2 = new UserEntity("user2", "user2@example.com",
                passwordEncoder.encode("Password123!"));
        testUser2.setEnabled(true);
        testUser2.setRoles(Set.of(userRole));
        testUser2 = userRepository.save(testUser2);

        testRun = new RunEntity(
                UUID.randomUUID(),
                "test-owner/test-repo",
                123,
                "code",
                RunStatus.DEVELOPER,
                Instant.now()
        );
        testRun = runRepository.save(testRun);

        wsUrl = "ws://localhost:" + port + "/ws";
    }

    @Test
    void testWebSocketConnectionAndJoin() throws Exception {
        WebSocketStompClient stompClient = createStompClient();
        StompSession session = connectToWebSocket(stompClient, testUser1);

        assertNotNull(session, "WebSocket session should be established");
        assertTrue(session.isConnected(), "Session should be connected");

        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        session.subscribe("/topic/runs/" + testRun.getId() + "/collaboration",
                new TestStompFrameHandler(future));

        Map<String, Object> joinMessage = new HashMap<>();
        session.send("/app/runs/" + testRun.getId() + "/join", joinMessage);

        Map<String, Object> receivedMessage = future.get(5, TimeUnit.SECONDS);
        assertNotNull(receivedMessage, "Should receive join notification");
        assertEquals("USER_JOIN", receivedMessage.get("eventType"));

        session.disconnect();
        stompClient.stop();
    }

    @Test
    void testConcurrentGraftMutations() throws Exception {
        WebSocketStompClient client1 = createStompClient();
        WebSocketStompClient client2 = createStompClient();

        StompSession session1 = connectToWebSocket(client1, testUser1);
        StompSession session2 = connectToWebSocket(client2, testUser2);

        CompletableFuture<Map<String, Object>> future1 = new CompletableFuture<>();
        CompletableFuture<Map<String, Object>> future2 = new CompletableFuture<>();

        session1.subscribe("/topic/runs/" + testRun.getId() + "/collaboration",
                new TestStompFrameHandler(future1));
        session2.subscribe("/topic/runs/" + testRun.getId() + "/collaboration",
                new TestStompFrameHandler(future2));

        Thread.sleep(500);

        Map<String, Object> graftData1 = new HashMap<>();
        graftData1.put("after", "step1");
        graftData1.put("agentName", "SecurityEngineer");
        session1.send("/app/runs/" + testRun.getId() + "/graft", graftData1);

        Map<String, Object> graftData2 = new HashMap<>();
        graftData2.put("after", "step2");
        graftData2.put("agentName", "CodeQualityEngineer");
        session2.send("/app/runs/" + testRun.getId() + "/graft", graftData2);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<CollaborationEventEntity> events = eventRepository
                    .findTop100ByRunIdOrderByTimestampDesc(testRun.getId());
            long graftCount = events.stream()
                    .filter(e -> "GRAFT".equals(e.getEventType()))
                    .count();
            assertEquals(2, graftCount, "Should have 2 graft events");
        });

        List<PersistedCollaborationMessage> messages = messageRepository
                .findByRunIdOrderBySequenceNumberDesc(testRun.getId());
        assertEquals(2, messages.size(), "Should have 2 persisted critical messages");
        assertTrue(messages.stream().allMatch(PersistedCollaborationMessage::getIsCritical),
                "Graft messages should be marked as critical");

        session1.disconnect();
        session2.disconnect();
        client1.stop();
        client2.stop();
    }

    @Test
    void testConcurrentPruneMutations() throws Exception {
        WebSocketStompClient client1 = createStompClient();
        WebSocketStompClient client2 = createStompClient();

        StompSession session1 = connectToWebSocket(client1, testUser1);
        StompSession session2 = connectToWebSocket(client2, testUser2);

        session1.subscribe("/topic/runs/" + testRun.getId() + "/collaboration",
                new TestStompFrameHandler(new CompletableFuture<>()));
        session2.subscribe("/topic/runs/" + testRun.getId() + "/collaboration",
                new TestStompFrameHandler(new CompletableFuture<>()));

        Thread.sleep(500);

        Map<String, Object> pruneData1 = new HashMap<>();
        pruneData1.put("stepId", "step-a");
        pruneData1.put("isPruned", true);
        session1.send("/app/runs/" + testRun.getId() + "/prune", pruneData1);

        Map<String, Object> pruneData2 = new HashMap<>();
        pruneData2.put("stepId", "step-b");
        pruneData2.put("isPruned", true);
        session2.send("/app/runs/" + testRun.getId() + "/prune", pruneData2);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<CollaborationEventEntity> events = eventRepository
                    .findTop100ByRunIdOrderByTimestampDesc(testRun.getId());
            long pruneCount = events.stream()
                    .filter(e -> "PRUNE".equals(e.getEventType()))
                    .count();
            assertEquals(2, pruneCount, "Should have 2 prune events");
        });

        RunEntity updatedRun = runRepository.findById(testRun.getId()).orElseThrow();
        assertNotNull(updatedRun.getPrunedSteps());
        assertTrue(updatedRun.getPrunedSteps().contains("step-a"));
        assertTrue(updatedRun.getPrunedSteps().contains("step-b"));

        session1.disconnect();
        session2.disconnect();
        client1.stop();
        client2.stop();
    }

    @Test
    void testFlagMutations() throws Exception {
        WebSocketStompClient client = createStompClient();
        StompSession session = connectToWebSocket(client, testUser1);

        CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
        session.subscribe("/topic/runs/" + testRun.getId() + "/collaboration",
                new TestStompFrameHandler(future));

        Thread.sleep(500);

        Map<String, Object> flagData = new HashMap<>();
        flagData.put("nodeId", "node-123");
        flagData.put("flagType", "ATTENTION_REQUIRED");
        flagData.put("comment", "Needs review");
        session.send("/app/runs/" + testRun.getId() + "/flag", flagData);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<CollaborationEventEntity> events = eventRepository
                    .findTop100ByRunIdOrderByTimestampDesc(testRun.getId());
            assertTrue(events.stream().anyMatch(e -> "FLAG".equals(e.getEventType())),
                    "Should have flag event");
        });

        session.disconnect();
        client.stop();
    }

    @Test
    void testCursorTracking() throws Exception {
        WebSocketStompClient client1 = createStompClient();
        WebSocketStompClient client2 = createStompClient();

        StompSession session1 = connectToWebSocket(client1, testUser1);
        StompSession session2 = connectToWebSocket(client2, testUser2);

        List<Map<String, Object>> receivedMessages = new CopyOnWriteArrayList<>();
        session2.subscribe("/topic/runs/" + testRun.getId() + "/collaboration",
                new StompFrameHandler() {
                    @Override
                    public Type getPayloadType(StompHeaders headers) {
                        return Map.class;
                    }

                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        receivedMessages.add((Map<String, Object>) payload);
                    }
                });

        Thread.sleep(500);

        Map<String, String> cursorData = new HashMap<>();
        cursorData.put("nodeId", "node-456");
        session1.send("/app/runs/" + testRun.getId() + "/cursor", cursorData);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertTrue(receivedMessages.stream()
                    .anyMatch(m -> "CURSOR_MOVE".equals(m.get("eventType"))),
                    "Should receive cursor move event");
        });

        session1.disconnect();
        session2.disconnect();
        client1.stop();
        client2.stop();
    }

    @Test
    void testHttpPollingFallback() throws Exception {
        WebSocketStompClient client = createStompClient();
        StompSession session = connectToWebSocket(client, testUser1);

        session.subscribe("/topic/runs/" + testRun.getId() + "/collaboration",
                new TestStompFrameHandler(new CompletableFuture<>()));

        Thread.sleep(500);

        Map<String, Object> graftData = new HashMap<>();
        graftData.put("after", "step1");
        graftData.put("agentName", "TestAgent");
        session.send("/app/runs/" + testRun.getId() + "/graft", graftData);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<PersistedCollaborationMessage> messages = messageRepository
                    .findByRunIdOrderBySequenceNumberDesc(testRun.getId());
            assertFalse(messages.isEmpty(), "Should have persisted messages");
        });

        ResponseEntity<String> pollResponse = restTemplate.exchange(
                "/api/runs/" + testRun.getId() + "/collaboration/poll",
                HttpMethod.GET,
                null,
                String.class
        );

        assertEquals(HttpStatus.OK, pollResponse.getStatusCode());
        JsonNode pollJson = objectMapper.readTree(pollResponse.getBody());

        assertTrue(pollJson.has("events"), "Should have events array");
        assertTrue(pollJson.get("events").isArray(), "Events should be an array");
        assertFalse(pollJson.get("events").isEmpty(), "Should have at least one event");

        session.disconnect();
        client.stop();
    }

    @Test
    void testMessageSequenceNumbers() throws Exception {
        WebSocketStompClient client = createStompClient();
        StompSession session = connectToWebSocket(client, testUser1);

        session.subscribe("/topic/runs/" + testRun.getId() + "/collaboration",
                new TestStompFrameHandler(new CompletableFuture<>()));

        Thread.sleep(500);

        for (int i = 0; i < 5; i++) {
            Map<String, Object> graftData = new HashMap<>();
            graftData.put("after", "step" + i);
            graftData.put("agentName", "Agent" + i);
            session.send("/app/runs/" + testRun.getId() + "/graft", graftData);
            Thread.sleep(100);
        }

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            List<PersistedCollaborationMessage> messages = messageRepository
                    .findByRunIdOrderBySequenceNumberDesc(testRun.getId());
            assertEquals(5, messages.size(), "Should have 5 persisted messages");
        });

        List<PersistedCollaborationMessage> messages = messageRepository
                .findByRunIdOrderBySequenceNumberDesc(testRun.getId());

        Set<Long> sequenceNumbers = new HashSet<>();
        for (PersistedCollaborationMessage msg : messages) {
            sequenceNumbers.add(msg.getSequenceNumber());
        }

        assertEquals(5, sequenceNumbers.size(), "All sequence numbers should be unique");
        assertTrue(sequenceNumbers.stream().allMatch(seq -> seq > 0),
                "All sequence numbers should be positive");

        session.disconnect();
        client.stop();
    }

    @Test
    void testCollaborationReplayEndpoint() throws Exception {
        WebSocketStompClient client = createStompClient();
        StompSession session = connectToWebSocket(client, testUser1);

        session.subscribe("/topic/runs/" + testRun.getId() + "/collaboration",
                new TestStompFrameHandler(new CompletableFuture<>()));

        Thread.sleep(500);

        Map<String, Object> graftData = new HashMap<>();
        graftData.put("after", "step1");
        graftData.put("agentName", "TestAgent");
        session.send("/app/runs/" + testRun.getId() + "/graft", graftData);

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            List<PersistedCollaborationMessage> messages = messageRepository
                    .findByRunIdOrderBySequenceNumberDesc(testRun.getId());
            assertFalse(messages.isEmpty());
        });

        ResponseEntity<String> replayResponse = restTemplate.exchange(
                "/api/runs/" + testRun.getId() + "/collaboration/replay",
                HttpMethod.GET,
                null,
                String.class
        );

        assertEquals(HttpStatus.OK, replayResponse.getStatusCode());
        JsonNode replayJson = objectMapper.readTree(replayResponse.getBody());

        assertTrue(replayJson.has("events"), "Should have events array");
        assertTrue(replayJson.has("totalEvents"), "Should have total events count");
        assertFalse(replayJson.get("events").isEmpty(), "Should have at least one event");

        session.disconnect();
        client.stop();
    }

    private WebSocketStompClient createStompClient() {
        WebSocketStompClient stompClient = new WebSocketStompClient(
                new SockJsClient(List.of(new WebSocketTransport(new StandardWebSocketClient()))));
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
        return stompClient;
    }

    private StompSession connectToWebSocket(WebSocketStompClient client, UserEntity user)
            throws ExecutionException, InterruptedException, TimeoutException {
        String token = jwtService.generateAccessToken(user);
        
        WebSocketHttpHeaders handshakeHeaders = new WebSocketHttpHeaders();
        handshakeHeaders.add("Authorization", "Bearer " + token);
        
        StompHeaders connectHeaders = new StompHeaders();

        return client.connectAsync(wsUrl, handshakeHeaders, connectHeaders, new StompSessionHandlerAdapter() {
        }).get(5, TimeUnit.SECONDS);
    }

    private static class TestStompFrameHandler implements StompFrameHandler {
        private final CompletableFuture<Map<String, Object>> future;

        public TestStompFrameHandler(CompletableFuture<Map<String, Object>> future) {
            this.future = future;
        }

        @Override
        public Type getPayloadType(StompHeaders headers) {
            return Map.class;
        }

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            future.complete((Map<String, Object>) payload);
        }
    }
}
