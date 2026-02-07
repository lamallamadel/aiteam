package com.atlasia.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

@Service
public class JsonSchemaValidator {
    private final ObjectMapper objectMapper;
    private final JsonSchemaFactory schemaFactory;

    public JsonSchemaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
    }

    public void validate(String json, String schemaFileName) throws IOException {
        Path schemaPath = Paths.get("ai/schemas/" + schemaFileName);
        String schemaContent = Files.readString(schemaPath);
        
        JsonNode schemaNode = objectMapper.readTree(schemaContent);
        JsonNode jsonNode = objectMapper.readTree(json);
        
        JsonSchema schema = schemaFactory.getSchema(schemaNode);
        Set<ValidationMessage> errors = schema.validate(jsonNode);
        
        if (!errors.isEmpty()) {
            StringBuilder errorMessage = new StringBuilder("JSON validation failed for " + schemaFileName + ": ");
            errors.forEach(error -> errorMessage.append(error.getMessage()).append("; "));
            throw new IllegalArgumentException(errorMessage.toString());
        }
    }
}
