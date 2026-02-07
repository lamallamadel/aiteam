package com.atlasia.ai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class PersonaConfigLoader {
    
    private static final Logger log = LoggerFactory.getLogger(PersonaConfigLoader.class);
    private static final String PERSONAS_PATH = "classpath:ai/agents/personas/*.yaml";
    
    private final List<PersonaConfig> personas = new ArrayList<>();
    private final ObjectMapper yamlMapper;
    
    public PersonaConfigLoader() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.yamlMapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }
    
    @PostConstruct
    public void loadPersonas() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources(PERSONAS_PATH);
            
            log.info("Loading {} persona configuration(s) from {}", resources.length, PERSONAS_PATH);
            
            for (Resource resource : resources) {
                try {
                    PersonaConfig persona = yamlMapper.readValue(
                        resource.getInputStream(),
                        PersonaConfig.class
                    );
                    personas.add(persona);
                    log.info("Loaded persona: {} ({})", persona.name(), persona.role());
                } catch (IOException e) {
                    log.error("Failed to load persona from {}: {}", resource.getFilename(), e.getMessage());
                }
            }
            
            log.info("Successfully loaded {} persona(s)", personas.size());
            
        } catch (IOException e) {
            log.error("Failed to scan for persona configurations: {}", e.getMessage());
        }
    }
    
    public List<PersonaConfig> getPersonas() {
        return List.copyOf(personas);
    }
    
    public PersonaConfig getPersonaByName(String name) {
        return personas.stream()
                .filter(p -> p.name().equalsIgnoreCase(name))
                .findFirst()
                .orElse(null);
    }
}
