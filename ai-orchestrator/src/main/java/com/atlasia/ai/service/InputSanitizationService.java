package com.atlasia.ai.service;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

@Service
public class InputSanitizationService {

    private static final PolicyFactory HTML_POLICY = new HtmlPolicyBuilder()
            .allowElements("b", "i", "u", "em", "strong", "p", "br", "span", "div", "h1", "h2", "h3", "h4", "h5", "h6",
                    "ul", "ol", "li", "a", "code", "pre", "blockquote")
            .allowAttributes("href").onElements("a")
            .allowAttributes("class").onElements("code", "pre", "span", "div")
            .requireRelNofollowOnLinks()
            .toFactory();

    private static final Pattern PATH_TRAVERSAL_PATTERN = Pattern.compile(".*[/\\\\]\\.\\.([/\\\\].*)?");
    private static final Pattern INVALID_FILENAME_CHARS = Pattern.compile("[^a-zA-Z0-9._-]");
    private static final Pattern VALID_REPO_PATH = Pattern.compile("^[a-zA-Z0-9_-]+/[a-zA-Z0-9_.-]+$");

    public String sanitizeHtml(String input) {
        if (input == null) {
            return null;
        }
        return HTML_POLICY.sanitize(input);
    }

    public String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return null;
        }
        
        if (PATH_TRAVERSAL_PATTERN.matcher(fileName).matches()) {
            throw new IllegalArgumentException("Path traversal detected in filename");
        }
        
        String sanitized = fileName.replaceAll("\\.\\.", "");
        sanitized = sanitized.replaceAll("[/\\\\]", "");
        sanitized = sanitized.replaceAll("[^a-zA-Z0-9._-]", "_");
        
        if (sanitized.isEmpty()) {
            throw new IllegalArgumentException("Filename is empty after sanitization");
        }
        
        return sanitized;
    }

    public boolean validateRepositoryPath(String path) {
        if (path == null || path.trim().isEmpty()) {
            return false;
        }
        return VALID_REPO_PATH.matcher(path).matches();
    }

    public boolean isValidFileName(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) {
            return false;
        }
        
        if (PATH_TRAVERSAL_PATTERN.matcher(fileName).matches()) {
            return false;
        }
        
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            return false;
        }
        
        return fileName.length() <= 255 && fileName.matches("[a-zA-Z0-9._-]+");
    }

    public boolean isSafeHtml(String input) {
        if (input == null) {
            return true;
        }
        String sanitized = sanitizeHtml(input);
        return sanitized.equals(input);
    }
}
