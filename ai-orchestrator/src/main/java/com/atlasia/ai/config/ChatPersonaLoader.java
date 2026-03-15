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
 * Loads persona definitions from classpath:ai/agents/personas/*.yaml — the single source of truth.
 *
 * Handles both schemas:
 * - Chat Mode personas: use the {@code identity} field as the LLM identity brief.
 * - Code Mode review personas: use the {@code persona} field as fallback when {@code identity} is absent.
 *
 * Personas without an {@code id} field use their filename (minus .yaml) as the key.
 *
 * System prompts are assembled by injecting each section in a fixed order so the LLM
 * receives a coherent, structured identity brief before any user message.
 */
@Component
public class ChatPersonaLoader {

    private static final Logger log = LoggerFactory.getLogger(ChatPersonaLoader.class);
    private static final String PERSONAS_PATH = "classpath:ai/agents/personas/*.yaml";

    /** Raw parsed YAML maps, keyed by persona id */
    private final Map<String, Map<String, Object>> personas = new HashMap<>();

    @PostConstruct
    public void load() {
        Yaml yaml = new Yaml();
        var resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources(PERSONAS_PATH);
            for (Resource resource : resources) {
                try (InputStream is = resource.getInputStream()) {
                    Map<String, Object> data = yaml.load(is);
                    // id field preferred; fall back to filename without extension
                    String id = (String) data.get("id");
                    if (id == null || id.isBlank()) {
                        String filename = resource.getFilename();
                        id = filename != null ? filename.replace(".yaml", "") : null;
                    }
                    if (id != null) {
                        personas.put(id, data);
                        log.info("Loaded persona: {} ({})", data.get("name"), resource.getFilename());
                    } else {
                        log.warn("Persona file {} has no id and no filename — skipped", resource.getFilename());
                    }
                } catch (Exception e) {
                    log.error("Failed to load persona from {}: {}", resource.getFilename(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("No personas found at {}: {}", PERSONAS_PATH, e.getMessage());
        }
        log.info("Loaded {} persona(s)", personas.size());
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

    /** Returns the list of persona IDs this persona is allowed to hand off to. */
    @SuppressWarnings("unchecked")
    public List<String> getAllowedHandoffTargets(String personaId) {
        var p = personas.get(personaId);
        if (p == null) return List.of();
        var handoff = (Map<String, Object>) p.get("handoff");
        if (handoff == null) return List.of();
        var targets = (List<String>) handoff.get("can_hand_off_to");
        return targets != null ? targets : List.of();
    }

    /** Returns the default handoff target for this persona. */
    @SuppressWarnings("unchecked")
    public String getDefaultTarget(String personaId) {
        var p = personas.get(personaId);
        if (p == null) return null;
        var handoff = (Map<String, Object>) p.get("handoff");
        if (handoff == null) return null;
        return (String) handoff.get("default_target");
    }

    /** Returns the auto-trigger phrases for this persona's handoff. */
    @SuppressWarnings("unchecked")
    public List<String> getAutoTriggers(String personaId) {
        var p = personas.get(personaId);
        if (p == null) return List.of();
        var handoff = (Map<String, Object>) p.get("handoff");
        if (handoff == null) return List.of();
        var triggers = (List<String>) handoff.get("auto_trigger_when");
        return triggers != null ? triggers : List.of();
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

        // Chat Mode personas use `identity`; Code Mode review personas use `persona` as fallback
        String identity = getString(p, "identity");
        if (identity.isBlank()) identity = getString(p, "persona");
        append(sb, "# Identity\n", identity);
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
