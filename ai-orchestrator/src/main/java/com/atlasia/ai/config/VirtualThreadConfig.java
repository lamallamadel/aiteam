package com.atlasia.ai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configures the rate-limited virtual thread executor for LLM API calls.
 *
 * spring.threads.virtual.enabled=true (application.yml) already switches Tomcat's request
 * threads and the default @Async executor to virtual threads via Spring Boot auto-configuration.
 * This class adds only the additional {@code aiCallExecutor} bean — a bounded executor whose
 * concurrency limit acts as a semaphore, preventing thundering-herd against the LLM endpoint.
 */
@Configuration
public class VirtualThreadConfig {

    private static final Logger log = LoggerFactory.getLogger(VirtualThreadConfig.class);

    @Value("${atlasia.orchestrator.chat.ai-call-concurrency:20}")
    private int aiCallConcurrency;

    /**
     * Rate-limited virtual thread executor for LLM API calls.
     * All {@link AsyncPersonaService} calls run on this executor.
     * Named threads follow the "ai-vt-N" convention for observability.
     */
    @Bean(name = "aiCallExecutor")
    public Executor aiCallExecutor() {
        var exec = new SimpleAsyncTaskExecutor("ai-vt-");
        exec.setVirtualThreads(true);
        exec.setConcurrencyLimit(aiCallConcurrency);
        log.info("aiCallExecutor configured: virtual threads, concurrency limit={}", aiCallConcurrency);
        return exec;
    }
}
