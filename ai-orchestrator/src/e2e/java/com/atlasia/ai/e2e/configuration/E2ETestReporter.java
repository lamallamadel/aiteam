package com.atlasia.ai.e2e.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class E2ETestReporter {

    private static final Logger logger = LoggerFactory.getLogger(E2ETestReporter.class);
    private static final String REPORT_DIR = "target/e2e-test-reports";
    private static final String SCREENSHOTS_DIR = REPORT_DIR + "/screenshots";
    private static final DateTimeFormatter TIMESTAMP_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").withZone(ZoneId.systemDefault());
    
    private final Map<String, TestResult> testResults = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public E2ETestReporter() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        ensureDirectoriesExist();
    }

    public void recordTestResult(TestInfo testInfo) {
        String testName = testInfo.getDisplayName();
        String className = testInfo.getTestClass().map(Class::getSimpleName).orElse("Unknown");
        
        TestResult result = new TestResult();
        result.testName = testName;
        result.className = className;
        result.timestamp = Instant.now();
        result.status = "PASSED";
        
        testResults.put(className + "." + testName, result);
    }

    public void recordFailure(TestInfo testInfo, Throwable throwable, byte[] screenshot) {
        String testName = testInfo.getDisplayName();
        String className = testInfo.getTestClass().map(Class::getSimpleName).orElse("Unknown");
        String key = className + "." + testName;
        
        TestResult result = testResults.getOrDefault(key, new TestResult());
        result.testName = testName;
        result.className = className;
        result.timestamp = Instant.now();
        result.status = "FAILED";
        result.errorMessage = throwable.getMessage();
        result.stackTrace = getStackTrace(throwable);
        
        if (screenshot != null) {
            String screenshotPath = saveScreenshot(className, testName, screenshot);
            result.screenshotPath = screenshotPath;
        }
        
        testResults.put(key, result);
    }

    public void captureScreenshotOnFailure(String testClassName, String testName, String htmlContent) {
        try {
            BufferedImage image = renderHtmlToImage(htmlContent);
            String timestamp = TIMESTAMP_FORMATTER.format(Instant.now());
            String filename = String.format("%s_%s_%s.png", testClassName, testName, timestamp);
            File screenshotFile = new File(SCREENSHOTS_DIR, filename);
            
            ImageIO.write(image, "png", screenshotFile);
            logger.info("Screenshot saved: {}", screenshotFile.getAbsolutePath());
        } catch (Exception e) {
            logger.error("Failed to capture screenshot: {}", e.getMessage(), e);
        }
    }

    public void generateReport() {
        try {
            File reportFile = new File(REPORT_DIR, "e2e-test-report.json");
            
            Map<String, Object> report = new HashMap<>();
            report.put("generatedAt", Instant.now().toString());
            report.put("totalTests", testResults.size());
            report.put("passedTests", testResults.values().stream().filter(r -> "PASSED".equals(r.status)).count());
            report.put("failedTests", testResults.values().stream().filter(r -> "FAILED".equals(r.status)).count());
            report.put("results", new ArrayList<>(testResults.values()));
            
            objectMapper.writeValue(reportFile, report);
            
            generateHtmlReport(report);
            
            logger.info("E2E test report generated: {}", reportFile.getAbsolutePath());
        } catch (Exception e) {
            logger.error("Failed to generate test report: {}", e.getMessage(), e);
        }
    }

    private void generateHtmlReport(Map<String, Object> reportData) {
        try {
            File htmlFile = new File(REPORT_DIR, "e2e-test-report.html");
            
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html>\n");
            html.append("<html>\n<head>\n");
            html.append("<title>E2E Test Report</title>\n");
            html.append("<style>\n");
            html.append("body { font-family: Arial, sans-serif; margin: 20px; background: #f5f5f5; }\n");
            html.append("h1 { color: #333; }\n");
            html.append(".summary { background: white; padding: 20px; border-radius: 5px; margin-bottom: 20px; }\n");
            html.append(".test-result { background: white; padding: 15px; margin: 10px 0; border-radius: 5px; border-left: 5px solid; }\n");
            html.append(".passed { border-left-color: #4caf50; }\n");
            html.append(".failed { border-left-color: #f44336; }\n");
            html.append(".error { color: #f44336; margin-top: 10px; }\n");
            html.append(".screenshot { max-width: 100%; margin-top: 10px; border: 1px solid #ddd; }\n");
            html.append(".stacktrace { background: #f9f9f9; padding: 10px; overflow-x: auto; font-family: monospace; font-size: 12px; }\n");
            html.append("</style>\n");
            html.append("</head>\n<body>\n");
            
            html.append("<h1>E2E Test Report</h1>\n");
            html.append("<div class='summary'>\n");
            html.append("<h2>Summary</h2>\n");
            html.append("<p><strong>Generated At:</strong> ").append(reportData.get("generatedAt")).append("</p>\n");
            html.append("<p><strong>Total Tests:</strong> ").append(reportData.get("totalTests")).append("</p>\n");
            html.append("<p><strong>Passed:</strong> <span style='color: #4caf50'>").append(reportData.get("passedTests")).append("</span></p>\n");
            html.append("<p><strong>Failed:</strong> <span style='color: #f44336'>").append(reportData.get("failedTests")).append("</span></p>\n");
            html.append("</div>\n");
            
            html.append("<h2>Test Results</h2>\n");
            
            @SuppressWarnings("unchecked")
            List<TestResult> results = (List<TestResult>) reportData.get("results");
            for (TestResult result : results) {
                String statusClass = result.status.equalsIgnoreCase("PASSED") ? "passed" : "failed";
                html.append("<div class='test-result ").append(statusClass).append("'>\n");
                html.append("<h3>").append(result.className).append(".").append(result.testName).append("</h3>\n");
                html.append("<p><strong>Status:</strong> ").append(result.status).append("</p>\n");
                html.append("<p><strong>Timestamp:</strong> ").append(result.timestamp).append("</p>\n");
                
                if (result.errorMessage != null) {
                    html.append("<div class='error'>\n");
                    html.append("<strong>Error:</strong> ").append(escapeHtml(result.errorMessage)).append("\n");
                    html.append("</div>\n");
                }
                
                if (result.stackTrace != null) {
                    html.append("<details>\n");
                    html.append("<summary>Stack Trace</summary>\n");
                    html.append("<pre class='stacktrace'>").append(escapeHtml(result.stackTrace)).append("</pre>\n");
                    html.append("</details>\n");
                }
                
                if (result.screenshotPath != null) {
                    html.append("<details>\n");
                    html.append("<summary>Screenshot</summary>\n");
                    html.append("<img src='").append(result.screenshotPath).append("' class='screenshot' />\n");
                    html.append("</details>\n");
                }
                
                html.append("</div>\n");
            }
            
            html.append("</body>\n</html>");
            
            try (FileWriter writer = new FileWriter(htmlFile)) {
                writer.write(html.toString());
            }
            
            logger.info("HTML test report generated: {}", htmlFile.getAbsolutePath());
        } catch (Exception e) {
            logger.error("Failed to generate HTML report: {}", e.getMessage(), e);
        }
    }

    private String saveScreenshot(String className, String testName, byte[] screenshotData) {
        try {
            String timestamp = TIMESTAMP_FORMATTER.format(Instant.now());
            String filename = String.format("%s_%s_%s.png", className, testName, timestamp);
            File screenshotFile = new File(SCREENSHOTS_DIR, filename);
            
            java.nio.file.Files.write(screenshotFile.toPath(), screenshotData);
            
            return "screenshots/" + filename;
        } catch (Exception e) {
            logger.error("Failed to save screenshot: {}", e.getMessage(), e);
            return null;
        }
    }

    private BufferedImage renderHtmlToImage(String htmlContent) {
        BufferedImage image = new BufferedImage(1200, 800, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();
        
        g2d.setColor(Color.WHITE);
        g2d.fillRect(0, 0, 1200, 800);
        
        g2d.setColor(Color.BLACK);
        g2d.setFont(new Font("Monospaced", Font.PLAIN, 12));
        
        String[] lines = htmlContent.split("\n");
        int y = 20;
        for (int i = 0; i < Math.min(lines.length, 50); i++) {
            String line = lines[i];
            if (line.length() > 120) {
                line = line.substring(0, 120) + "...";
            }
            g2d.drawString(line, 10, y);
            y += 15;
        }
        
        g2d.dispose();
        return image;
    }

    private String getStackTrace(Throwable throwable) {
        StringBuilder sb = new StringBuilder();
        sb.append(throwable.toString()).append("\n");
        for (StackTraceElement element : throwable.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
        }
        if (throwable.getCause() != null) {
            sb.append("Caused by: ").append(getStackTrace(throwable.getCause()));
        }
        return sb.toString();
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#x27;");
    }

    private void ensureDirectoriesExist() {
        new File(REPORT_DIR).mkdirs();
        new File(SCREENSHOTS_DIR).mkdirs();
    }

    public static class TestResult {
        public String testName;
        public String className;
        public Instant timestamp;
        public String status;
        public String errorMessage;
        public String stackTrace;
        public String screenshotPath;
    }
}
