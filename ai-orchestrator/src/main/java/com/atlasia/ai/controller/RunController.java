package com.atlasia.ai.controller;

import com.atlasia.ai.api.RunRequest;
import com.atlasia.ai.api.RunResponse;
import com.atlasia.ai.config.OrchestratorProperties;
import com.atlasia.ai.model.RunEntity;
import com.atlasia.ai.model.RunStatus;
import com.atlasia.ai.persistence.RunRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/runs")
public class RunController {

    private final RunRepository runRepository;
    private final OrchestratorProperties props;

    public RunController(RunRepository runRepository, OrchestratorProperties props) {
        this.runRepository = runRepository;
        this.props = props;
    }

    @PostMapping
    public ResponseEntity<RunResponse> createRun(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody RunRequest request
    ) {
        if (!isAuthorized(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UUID id = UUID.randomUUID();
        RunEntity entity = new RunEntity(
                id,
                request.repo(),
                request.issueNumber(),
                request.mode(),
                RunStatus.RECEIVED,
                Instant.now()
        );
        runRepository.save(entity);

        // Scaffold: dans une version complète, on enqueue un job ici (async) et on exécute le workflow.
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new RunResponse(id, entity.getStatus().name(), entity.getCreatedAt()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RunEntity> get(@RequestHeader(value = "Authorization", required = false) String authorization,
                                         @PathVariable("id") UUID id) {
        if (!isAuthorized(authorization)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return runRepository.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    private boolean isAuthorized(String authorization) {
        if (!StringUtils.hasText(props.token())) return false;
        if (!StringUtils.hasText(authorization)) return false;

        String prefix = "Bearer ";
        if (!authorization.startsWith(prefix)) return false;

        String token = authorization.substring(prefix.length()).trim();
        return props.token().equals(token);
    }
}
