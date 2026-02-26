package com.atlasia.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class MonorepoWorkspaceDetector {
    private static final Logger log = LoggerFactory.getLogger(MonorepoWorkspaceDetector.class);

    private final ObjectMapper objectMapper;
    private final GitHubApiClient gitHubApiClient;

    public MonorepoWorkspaceDetector(ObjectMapper objectMapper, GitHubApiClient gitHubApiClient) {
        this.objectMapper = objectMapper;
        this.gitHubApiClient = gitHubApiClient;
    }

    public WorkspaceDetectionResult detectWorkspace(String owner, String repo) {
        try {
            WorkspaceDetectionResult mavenResult = detectMavenModules(owner, repo);
            if (mavenResult.isWorkspace()) {
                return mavenResult;
            }

            WorkspaceDetectionResult npmResult = detectNpmWorkspaces(owner, repo);
            if (npmResult.isWorkspace()) {
                return npmResult;
            }

            return new WorkspaceDetectionResult(false, null, null);
        } catch (Exception e) {
            log.error("Failed to detect workspace for {}/{}: {}", owner, repo, e.getMessage(), e);
            return new WorkspaceDetectionResult(false, null, null);
        }
    }

    private WorkspaceDetectionResult detectMavenModules(String owner, String repo) {
        try {
            Map<String, Object> pomContent = gitHubApiClient.getRepoContent(owner, repo, "pom.xml");
            if (pomContent == null || !pomContent.containsKey("content")) {
                return new WorkspaceDetectionResult(false, null, null);
            }

            String contentEncoded = (String) pomContent.get("content");
            String pomXml = new String(Base64.getDecoder().decode(contentEncoded), StandardCharsets.UTF_8);

            List<String> modules = parseMavenModules(pomXml);
            if (modules.isEmpty()) {
                return new WorkspaceDetectionResult(false, null, null);
            }

            Map<String, Object> config = Map.of(
                "modules", modules,
                "root", ".",
                "buildFile", "pom.xml"
            );

            String configJson = objectMapper.writeValueAsString(config);
            log.info("Detected Maven multi-module project in {}/{} with {} modules", owner, repo, modules.size());
            return new WorkspaceDetectionResult(true, "maven_modules", configJson);

        } catch (Exception e) {
            log.debug("No Maven modules detected in {}/{}: {}", owner, repo, e.getMessage());
            return new WorkspaceDetectionResult(false, null, null);
        }
    }

    private WorkspaceDetectionResult detectNpmWorkspaces(String owner, String repo) {
        try {
            Map<String, Object> packageJsonContent = gitHubApiClient.getRepoContent(owner, repo, "package.json");
            if (packageJsonContent == null || !packageJsonContent.containsKey("content")) {
                return new WorkspaceDetectionResult(false, null, null);
            }

            String contentEncoded = (String) packageJsonContent.get("content");
            String packageJsonStr = new String(Base64.getDecoder().decode(contentEncoded), StandardCharsets.UTF_8);

            JsonNode packageJson = objectMapper.readTree(packageJsonStr);
            JsonNode workspacesNode = packageJson.get("workspaces");
            
            if (workspacesNode == null) {
                return new WorkspaceDetectionResult(false, null, null);
            }

            List<String> workspaces = new ArrayList<>();
            if (workspacesNode.isArray()) {
                workspacesNode.forEach(node -> workspaces.add(node.asText()));
            } else if (workspacesNode.isObject() && workspacesNode.has("packages")) {
                workspacesNode.get("packages").forEach(node -> workspaces.add(node.asText()));
            }

            if (workspaces.isEmpty()) {
                return new WorkspaceDetectionResult(false, null, null);
            }

            Map<String, Object> config = Map.of(
                "workspaces", workspaces,
                "root", ".",
                "packageManager", detectPackageManager(owner, repo)
            );

            String configJson = objectMapper.writeValueAsString(config);
            log.info("Detected NPM workspaces in {}/{} with {} workspaces", owner, repo, workspaces.size());
            return new WorkspaceDetectionResult(true, "npm_workspaces", configJson);

        } catch (Exception e) {
            log.debug("No NPM workspaces detected in {}/{}: {}", owner, repo, e.getMessage());
            return new WorkspaceDetectionResult(false, null, null);
        }
    }

    private List<String> parseMavenModules(String pomXml) throws Exception {
        List<String> modules = new ArrayList<>();
        
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(pomXml.getBytes(StandardCharsets.UTF_8)));

        NodeList moduleNodes = doc.getElementsByTagName("module");
        for (int i = 0; i < moduleNodes.getLength(); i++) {
            Element moduleElement = (Element) moduleNodes.item(i);
            String moduleName = moduleElement.getTextContent().trim();
            if (!moduleName.isEmpty()) {
                modules.add(moduleName);
            }
        }

        return modules;
    }

    private String detectPackageManager(String owner, String repo) {
        try {
            Map<String, Object> yarnLock = gitHubApiClient.getRepoContent(owner, repo, "yarn.lock");
            if (yarnLock != null) {
                return "yarn";
            }
        } catch (Exception ignored) {}

        try {
            Map<String, Object> pnpmLock = gitHubApiClient.getRepoContent(owner, repo, "pnpm-lock.yaml");
            if (pnpmLock != null) {
                return "pnpm";
            }
        } catch (Exception ignored) {}

        return "npm";
    }

    public static class WorkspaceDetectionResult {
        private final boolean isWorkspace;
        private final String workspaceType;
        private final String workspaceConfig;

        public WorkspaceDetectionResult(boolean isWorkspace, String workspaceType, String workspaceConfig) {
            this.isWorkspace = isWorkspace;
            this.workspaceType = workspaceType;
            this.workspaceConfig = workspaceConfig;
        }

        public boolean isWorkspace() { return isWorkspace; }
        public String getWorkspaceType() { return workspaceType; }
        public String getWorkspaceConfig() { return workspaceConfig; }
    }
}
