package com.atlasia.ai.e2e.configuration;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

public class E2ETestWatcher implements TestWatcher {

    private static final Logger logger = LoggerFactory.getLogger(E2ETestWatcher.class);
    private final E2ETestReporter testReporter;
    private final TestRestTemplate restTemplate;

    public E2ETestWatcher(E2ETestReporter testReporter, TestRestTemplate restTemplate) {
        this.testReporter = testReporter;
        this.restTemplate = restTemplate;
    }

    @Override
    public void testSuccessful(ExtensionContext context) {
        String testName = context.getDisplayName();
        String className = context.getTestClass().map(Class::getSimpleName).orElse("Unknown");
        logger.info("Test passed: {} in class {}", testName, className);
        testReporter.recordTestResult(testName, className);
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        String testName = context.getDisplayName();
        String className = context.getTestClass().map(Class::getSimpleName).orElse("Unknown");
        logger.error("Test failed: {} - {}", testName, cause.getMessage());
        
        try {
            String methodName = context.getTestMethod().map(m -> m.getName()).orElse("unknown");
            
            String htmlContent = captureCurrentPageState();
            
            if (htmlContent != null && !htmlContent.isEmpty()) {
                testReporter.captureScreenshotOnFailure(className, methodName, htmlContent);
            }
            
            testReporter.recordFailure(testName, className, cause, null);
        } catch (Exception e) {
            logger.error("Failed to capture failure information: {}", e.getMessage(), e);
        }
    }

    @Override
    public void testAborted(ExtensionContext context, Throwable cause) {
        logger.warn("Test aborted: {} - {}", context.getDisplayName(), cause.getMessage());
    }

    @Override
    public void testDisabled(ExtensionContext context, Optional<String> reason) {
        logger.info("Test disabled: {} - {}", context.getDisplayName(), reason.orElse("No reason"));
    }

    private String captureCurrentPageState() {
        try {
            HttpHeaders headers = new HttpHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                    "/actuator/health",
                    HttpMethod.GET,
                    entity,
                    String.class
            );
            
            return response.getBody();
        } catch (Exception e) {
            logger.debug("Could not capture page state: {}", e.getMessage());
            return "Unable to capture page state: " + e.getMessage();
        }
    }
}
