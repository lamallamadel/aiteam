package com.atlasia.ai.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Development helper that enables JVM-level virtual thread pinning detection.
 *
 * When atlasia.orchestrator.debug.detect-pinning=true, sets the system property
 * jdk.tracePinnedThreads=full, causing the JVM to print a stack trace whenever a
 * virtual thread is pinned to a carrier thread (e.g., inside a synchronized block
 * or a native frame).
 *
 * Only enable in development — has a measurable overhead in production.
 * Set DETECT_VT_PINNING=true in the dev environment to activate.
 */
@Component
public class VirtualThreadPinningDetector {

    private static final Logger log = LoggerFactory.getLogger(VirtualThreadPinningDetector.class);

    @Value("${atlasia.orchestrator.debug.detect-pinning:false}")
    private boolean detectPinning;

    @PostConstruct
    public void configure() {
        if (detectPinning) {
            System.setProperty("jdk.tracePinnedThreads", "full");
            log.warn("Virtual thread pinning detection ENABLED — check logs for 'pinned' stack traces. Do not use in production.");
        }
    }
}
