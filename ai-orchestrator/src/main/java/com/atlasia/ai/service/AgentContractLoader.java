package com.atlasia.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Loads agent YAML contracts from the classpath ({@code ai/agents/*.yaml}) packaged at build time.
 */
@Component
public class AgentContractLoader {
    private static final Logger log = LoggerFactory.getLogger(AgentContractLoader.class);

    private static final String[] CONTRACT_FILES = {
            "pm", "qualifier", "architect", "developer", "tester", "writer", "review"
    };

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
    private final Map<String, AgentContract> contracts = new LinkedHashMap<>();

    @PostConstruct
    void loadContracts() {
        ClassLoader cl = getClass().getClassLoader();
        for (String base : CONTRACT_FILES) {
            String path = "ai/agents/" + base + ".yaml";
            try (InputStream in = cl.getResourceAsStream(path)) {
                if (in == null) {
                    log.debug("No agent contract on classpath: {}", path);
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> root = yamlMapper.readValue(in, Map.class);
                AgentContract c = parseContract(base, root);
                contracts.put(base, c);
                log.info("Loaded agent contract: {}", base);
            } catch (Exception e) {
                log.warn("Failed to load agent contract {}: {}", path, e.getMessage());
            }
        }
    }

    private AgentContract parseContract(String fallbackName, Map<String, Object> root) {
        String name = stringVal(root.get("name"), fallbackName);
        String persona = stringVal(root.get("persona"), "");
        String mission = stringVal(root.get("mission"), "");
        String cot = stringVal(root.get("chain_of_thought"), "");
        List<String> rules = parseRules(root.get("rules"));
        return new AgentContract(name, persona.trim(), mission.trim(), cot.trim(), rules);
    }

    private static String stringVal(Object o, String dflt) {
        if (o == null) {
            return dflt;
        }
        return o.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private static List<String> parseRules(Object rulesNode) {
        if (rulesNode == null) {
            return List.of();
        }
        if (rulesNode instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    out.add(item.toString().trim());
                }
            }
            return List.copyOf(out);
        }
        return List.of();
    }

    public AgentContract get(String agentYamlName) {
        return contracts.getOrDefault(agentYamlName, AgentContract.missing(agentYamlName));
    }

    /**
     * Text block prepended to the step-specific system prompt: persona, mission, chain-of-thought, rules.
     */
    public String systemPromptPrefix(String agentYamlName) {
        AgentContract c = get(agentYamlName);
        if (c.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (!c.persona().isBlank()) {
            sb.append(c.persona()).append("\n\n");
        }
        if (!c.mission().isBlank()) {
            sb.append("Mission: ").append(c.mission()).append("\n\n");
        }
        if (!c.chainOfThought().isBlank()) {
            sb.append(c.chainOfThought()).append("\n\n");
        }
        if (c.rules() != null && !c.rules().isEmpty()) {
            sb.append("Contract rules:\n");
            sb.append(c.rules().stream().map(r -> "- " + r).collect(Collectors.joining("\n")));
            sb.append("\n\n");
        }
        return sb.toString();
    }
}
