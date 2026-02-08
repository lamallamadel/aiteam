package com.atlasia.ai.controller;

import com.atlasia.ai.config.PersonaConfig;
import com.atlasia.ai.config.PersonaConfigLoader;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/personas")
public class PersonaController {

    private final PersonaConfigLoader personaConfigLoader;

    public PersonaController(PersonaConfigLoader personaConfigLoader) {
        this.personaConfigLoader = personaConfigLoader;
    }

    @GetMapping
    public ResponseEntity<List<PersonaConfig>> listPersonas() {
        return ResponseEntity.ok(personaConfigLoader.getPersonas());
    }
}
