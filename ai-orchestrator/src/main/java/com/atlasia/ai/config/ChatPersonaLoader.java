package com.atlasia.ai.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.*;

/**
 * Loads rich Chat Mode persona definitions from classpath:ai/agents/chat-personas/*.yaml.
 *
 * These are distinct from the flat Review personas (ai/agents/personas/) used in Code Mode.
 * The rich schema supports: identity, communication_style, skills, domain_knowledge,
 * constraints, anti_patterns, output_defaults, clarification, few_shot_examples, handoff.
 *
 * System prompts are assembled by injecting each section in a fixed order so the LLM
 * receives a coherent, structured identity brief before any user message.
 */
@Component
public class ChatPersonaLoader {

    private static final Logger log = LoggerFactory.getLogger(ChatPersonaLoader.class);
    private static final String CHAT_PERSONAS_PATH = "classpath:ai/agents/chat-personas/*.yaml";

    /** Raw parsed YAML maps, keyed by persona id */
    private final Map<String, Map<String, Object>> personas = new HashMap<>();

    @PostConstruct
    public void load() {
        Yaml yaml = new Yaml();
        var resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources(CHAT_PERSONAS_PATH);
            for (Resource resource : resources) {
                try (InputStream is = resource.getInputStream()) {
                    Map<String, Object> data = yaml.load(is);
                    String id = (String) data.get("id");
                    if (id != null) {
                        personas.put(id, data);
                        log.info("Loaded chat persona: {} ({})", data.get("name"), resource.getFilename());
                    } else {
                        log.warn("Chat persona file {} is missing the 'id' field — skipped", resource.getFilename());
                    }
                } catch (Exception e) {
                    log.error("Failed to load chat persona from {}: {}", resource.getFilename(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("No chat personas found at {}: {}", CHAT_PERSONAS_PATH, e.getMessage());
        }
        log.info("Loaded {} chat persona(s)", personas.size());
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    public boolean hasPersona(String personaId) {
        return personas.containsKey(personaId);
    }

    public List<String> listPersonaIds() {
        return new ArrayList<>(personas.keySet());
    }

    /**
     * Builds the full system prompt for a persona by assembling all YAML sections.
     * Section injection order:
     *   1. identity
     *   2. communication_style
     *   3. skills
     *   4. domain_knowledge
     *   5. constraints
     *   6. anti_patterns
     *   7. output_defaults
     *   8. clarification
     *   9. few_shot_examples
     */
    public String getSystemPrompt(String personaId) {
        var p = getPersona(personaId);
        var sb = new StringBuilder();

        append(sb, "# Identity\n", getString(p, "identity"));
        appendCommunicationStyle(sb, p);
        appendSkills(sb, p);
        append(sb, "\n# Domain knowledge\n", getString(p, "domain_knowledge"));
        appendConstraints(sb, p);
        appendAntiPatterns(sb, p);
        appendOutputDefaults(sb, p);
        appendClarification(sb, p);
        appendFewShotExamples(sb, p);

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Prompt section builders
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private void appendCommunicationStyle(StringBuilder sb, Map<String, Object> p) {
        var style = (Map<String, Object>) p.get("communication_style");
        if (style == null) return;
        sb.append("\n# Communication style\n");
        sb.append("Tone: ").append(style.getOrDefault("tone", "professional")).append("\n");
        sb.append("Ask clarifying questions: ").append(style.getOrDefault("asks_clarifying_questions", true)).append("\n");
        sb.append("Proactively suggest improvements: ").append(style.getOrDefault("proactive_suggestions", true)).append("\n");
    }

    @SuppressWarnings("unchecked")
    private void appendSkills(StringBuilder sb, Map<String, Object> p) {
        var skills = (List<Map<String, Object>>) p.get("skills");
        if (skills == null || skills.isEmpty()) return;
        sb.append("\n# Skills\n");
        skills.forEach(skill ->
            sb.append("- **").append(skill.get("name")).append("** (")
              .append(skill.get("proficiency")).append("): ")
              .append(skill.get("description")).append("\n")
        );
    }

    @SuppressWarnings("unchecked")
    private void appendConstraints(StringBuilder sb, Map<String, Object> p) {
        var constraints = (List<String>) p.get("constraints");
        if (constraints == null || constraints.isEmpty()) return;
        sb.append("\n# Hard constraints — never violate these\n");
        for (int i = 0; i < constraints.size(); i++) {
            sb.append(i + 1).append(". ").append(constraints.get(i)).append("\n");
        }
    }

    @SuppressWarnings("unchecked")
    private void appendAntiPatterns(StringBuilder sb, Map<String, Object> p) {
        var antiPatterns = (List<String>) p.get("anti_patterns");
        if (antiPatterns == null || antiPatterns.isEmpty()) return;
        sb.append("\n# Anti-patterns to actively avoid\n");
        antiPatterns.forEach(ap -> sb.append("- ").append(ap).append("\n"));
    }

    @SuppressWarnings("unchecked")
    private void appendOutputDefaults(StringBuilder sb, Map<String, Object> p) {
        var defaults = (Map<String, Object>) p.get("output_defaults");
        if (defaults == null) return;
        sb.append("\n# Output defaults — include in every response unless told otherwise\n");
        var always = (List<String>) defaults.get("always_include");
        if (always != null) always.forEach(item -> sb.append("- ").append(item).append("\n"));
        var pathPattern = (String) defaults.get("file_path_pattern");
        if (pathPattern != null) sb.append("File path pattern: ").append(pathPattern).append("\n");
        var libs = (List<String>) defaults.get("preferred_libraries");
        if (libs != null && !libs.isEmpty()) {
            sb.append("Preferred libraries: ").append(String.join(", ", libs)).append("\n");
        }
    }

    @SuppressWarnings("unchecked")
    private void appendClarification(StringBuilder sb, Map<String, Object> p) {
        var clar = (Map<String, Object>) p.get("clarification");
        if (clar == null) return;
        var askWhen    = (List<String>) clar.get("ask_before_acting_when");
        var assumeWhen = (List<String>) clar.get("assume_and_note_when");
        if (askWhen != null && !askWhen.isEmpty()) {
            sb.append("\n# When to ask clarifying questions before acting\n");
            askWhen.forEach(c -> sb.append("- ").append(c).append("\n"));
        }
        if (assumeWhen != null && !assumeWhen.isEmpty()) {
            sb.append("\n# When to make assumptions and document them\n");
            assumeWhen.forEach(c -> sb.append("- ").append(c).append("\n"));
        }
    }

    @SuppressWarnings("unchecked")
    private void appendFewShotExamples(StringBuilder sb, Map<String, Object> p) {
        var examples = (List<Map<String, Object>>) p.get("few_shot_examples");
        if (examples == null || examples.isEmpty()) return;
        sb.append("\n# Reference examples — follow this output pattern\n");
        for (var ex : examples) {
            sb.append("\n## Example: ").append(ex.get("title")).append("\n");
            sb.append("User: ").append(ex.get("user")).append("\n");
            if (ex.get("thinking") != null) {
                sb.append("Reasoning: ").append(ex.get("thinking")).append("\n");
            }
            sb.append("Expected response summary: ").append(ex.get("summary")).append("\n");
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Map<String, Object> getPersona(String personaId) {
        var p = personas.get(personaId);
        if (p == null) throw new IllegalArgumentException("Unknown chat persona: " + personaId);
        return p;
    }

    private String getString(Map<String, Object> map, String key) {
        var val = map.get(key);
        return val != null ? val.toString() : "";
    }

    private void append(StringBuilder sb, String header, String content) {
        if (content != null && !content.isBlank()) {
            sb.append(header).append(content).append("\n");
        }
    }
}
