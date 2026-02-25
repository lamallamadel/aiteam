package com.atlasia.ai.service;

import com.atlasia.ai.model.RepositoryGraphEntity;
import com.atlasia.ai.persistence.RepositoryGraphRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class MultiRepoScheduler {
    private static final Logger log = LoggerFactory.getLogger(MultiRepoScheduler.class);

    private final RepositoryGraphRepository repositoryGraphRepository;
    private final MonorepoWorkspaceDetector workspaceDetector;
    private final ObjectMapper objectMapper;

    public MultiRepoScheduler(
            RepositoryGraphRepository repositoryGraphRepository,
            MonorepoWorkspaceDetector workspaceDetector,
            ObjectMapper objectMapper) {
        this.repositoryGraphRepository = repositoryGraphRepository;
        this.workspaceDetector = workspaceDetector;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void registerRepository(String repoUrl, List<String> dependencies) {
        String normalizedUrl = normalizeRepoUrl(repoUrl);
        
        Optional<RepositoryGraphEntity> existing = repositoryGraphRepository.findByRepoUrl(normalizedUrl);
        
        try {
            String dependenciesJson = objectMapper.writeValueAsString(
                dependencies.stream()
                    .map(this::normalizeRepoUrl)
                    .collect(Collectors.toList())
            );

            if (existing.isPresent()) {
                RepositoryGraphEntity entity = existing.get();
                entity.setDependencies(dependenciesJson);
                repositoryGraphRepository.save(entity);
                log.info("Updated repository graph: repo={}, dependencies={}", normalizedUrl, dependencies.size());
            } else {
                RepositoryGraphEntity entity = new RepositoryGraphEntity(normalizedUrl, dependenciesJson);
                repositoryGraphRepository.save(entity);
                log.info("Registered repository in graph: repo={}, dependencies={}", normalizedUrl, dependencies.size());
            }
        } catch (Exception e) {
            log.error("Failed to register repository {}: {}", normalizedUrl, e.getMessage(), e);
            throw new RuntimeException("Failed to register repository", e);
        }
    }

    @Transactional
    public void detectAndRegisterWorkspace(String owner, String repo) {
        String repoUrl = normalizeRepoUrl(String.format("github.com/%s/%s", owner, repo));
        
        MonorepoWorkspaceDetector.WorkspaceDetectionResult result = workspaceDetector.detectWorkspace(owner, repo);
        
        Optional<RepositoryGraphEntity> existing = repositoryGraphRepository.findByRepoUrl(repoUrl);
        
        if (result.isWorkspace()) {
            if (existing.isPresent()) {
                RepositoryGraphEntity entity = existing.get();
                entity.setWorkspaceType(result.getWorkspaceType());
                entity.setWorkspaceConfig(result.getWorkspaceConfig());
                repositoryGraphRepository.save(entity);
                log.info("Updated workspace config: repo={}, type={}", repoUrl, result.getWorkspaceType());
            } else {
                RepositoryGraphEntity entity = new RepositoryGraphEntity(repoUrl, "[]");
                entity.setWorkspaceType(result.getWorkspaceType());
                entity.setWorkspaceConfig(result.getWorkspaceConfig());
                repositoryGraphRepository.save(entity);
                log.info("Registered workspace: repo={}, type={}", repoUrl, result.getWorkspaceType());
            }
        } else {
            log.info("No workspace detected for {}/{}", owner, repo);
        }
    }

    public List<String> computeExecutionOrder(Set<String> repoUrls) {
        Set<String> normalizedUrls = repoUrls.stream()
            .map(this::normalizeRepoUrl)
            .collect(Collectors.toSet());

        Map<String, List<String>> dependencyGraph = buildDependencyGraph(normalizedUrls);
        
        try {
            List<String> order = topologicalSort(dependencyGraph);
            log.info("Computed execution order for {} repositories: {}", order.size(), order);
            return order;
        } catch (CyclicDependencyException e) {
            log.error("Cyclic dependency detected in repository graph: {}", e.getMessage());
            throw e;
        }
    }

    public List<String> computeMergeOrder(Map<String, String> repoPrMapping) {
        Set<String> repoUrls = repoPrMapping.keySet().stream()
            .map(this::normalizeRepoUrl)
            .collect(Collectors.toSet());

        Map<String, List<String>> dependencyGraph = buildDependencyGraph(repoUrls);
        
        try {
            List<String> order = topologicalSort(dependencyGraph);
            log.info("Computed PR merge order for {} repositories: {}", order.size(), order);
            return order;
        } catch (CyclicDependencyException e) {
            log.error("Cyclic dependency detected for PR merge order: {}", e.getMessage());
            throw e;
        }
    }

    private Map<String, List<String>> buildDependencyGraph(Set<String> repoUrls) {
        Map<String, List<String>> graph = new HashMap<>();
        
        for (String repoUrl : repoUrls) {
            Optional<RepositoryGraphEntity> entityOpt = repositoryGraphRepository.findByRepoUrl(repoUrl);
            
            if (entityOpt.isPresent()) {
                try {
                    JsonNode depsNode = objectMapper.readTree(entityOpt.get().getDependencies());
                    List<String> dependencies = new ArrayList<>();
                    
                    if (depsNode.isArray()) {
                        depsNode.forEach(node -> {
                            String dep = normalizeRepoUrl(node.asText());
                            if (repoUrls.contains(dep)) {
                                dependencies.add(dep);
                            }
                        });
                    }
                    
                    graph.put(repoUrl, dependencies);
                } catch (Exception e) {
                    log.warn("Failed to parse dependencies for {}: {}", repoUrl, e.getMessage());
                    graph.put(repoUrl, Collections.emptyList());
                }
            } else {
                graph.put(repoUrl, Collections.emptyList());
            }
        }
        
        return graph;
    }

    private List<String> topologicalSort(Map<String, List<String>> graph) {
        Map<String, Integer> inDegree = new HashMap<>();
        
        for (String node : graph.keySet()) {
            inDegree.putIfAbsent(node, 0);
        }
        
        for (Map.Entry<String, List<String>> entry : graph.entrySet()) {
            for (String dep : entry.getValue()) {
                inDegree.put(dep, inDegree.getOrDefault(dep, 0) + 1);
            }
        }
        
        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }
        
        List<String> result = new ArrayList<>();
        
        while (!queue.isEmpty()) {
            String current = queue.poll();
            result.add(current);
            
            List<String> dependents = graph.getOrDefault(current, Collections.emptyList());
            for (String dependent : dependents) {
                int newDegree = inDegree.get(dependent) - 1;
                inDegree.put(dependent, newDegree);
                
                if (newDegree == 0) {
                    queue.offer(dependent);
                }
            }
        }
        
        if (result.size() != graph.size()) {
            Set<String> remaining = new HashSet<>(graph.keySet());
            remaining.removeAll(result);
            throw new CyclicDependencyException("Cyclic dependency detected involving: " + remaining);
        }
        
        return result;
    }

    public List<RepositoryGraphEntity> getDownstreamRepositories(String repoUrl) {
        String normalizedUrl = normalizeRepoUrl(repoUrl);
        return repositoryGraphRepository.findByDependsOn(normalizedUrl);
    }

    public Optional<RepositoryGraphEntity> getRepositoryGraph(String repoUrl) {
        return repositoryGraphRepository.findByRepoUrl(normalizeRepoUrl(repoUrl));
    }

    public List<RepositoryGraphEntity> getAllRepositories() {
        return repositoryGraphRepository.findAll();
    }

    private String normalizeRepoUrl(String repoUrl) {
        String normalized = repoUrl.toLowerCase()
            .replaceFirst("^https?://", "")
            .replaceFirst("\\.git$", "")
            .replaceFirst("/$", "");
        
        return normalized;
    }

    public static class CyclicDependencyException extends RuntimeException {
        public CyclicDependencyException(String message) {
            super(message);
        }
    }

    public record ExecutionPlan(
        List<String> executionOrder,
        Map<String, List<String>> dependencies,
        Map<String, RepositoryGraphEntity> repositoryMetadata
    ) {}

    public ExecutionPlan buildExecutionPlan(Set<String> repoUrls) {
        Set<String> normalizedUrls = repoUrls.stream()
            .map(this::normalizeRepoUrl)
            .collect(Collectors.toSet());

        Map<String, List<String>> dependencyGraph = buildDependencyGraph(normalizedUrls);
        List<String> executionOrder = topologicalSort(dependencyGraph);
        
        Map<String, RepositoryGraphEntity> metadata = new HashMap<>();
        for (String repoUrl : normalizedUrls) {
            repositoryGraphRepository.findByRepoUrl(repoUrl).ifPresent(entity -> 
                metadata.put(repoUrl, entity)
            );
        }

        return new ExecutionPlan(executionOrder, dependencyGraph, metadata);
    }
}
