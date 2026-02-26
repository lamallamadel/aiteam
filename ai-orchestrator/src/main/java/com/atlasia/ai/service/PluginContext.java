package com.atlasia.ai.service;

import java.util.HashMap;
import java.util.Map;

/**
 * Context passed to plugins during execution.
 * Provides access to run information and shared state.
 */
public class PluginContext {
    private final RunContext runContext;
    private final Map<String, Object> attributes;
    private final String pluginName;
    
    public PluginContext(RunContext runContext, String pluginName) {
        this.runContext = runContext;
        this.pluginName = pluginName;
        this.attributes = new HashMap<>();
    }
    
    public RunContext getRunContext() {
        return runContext;
    }
    
    public String getPluginName() {
        return pluginName;
    }
    
    public Map<String, Object> getAttributes() {
        return attributes;
    }
    
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }
    
    public Object getAttribute(String key) {
        return attributes.get(key);
    }
    
    public <T> T getAttribute(String key, Class<T> type) {
        Object value = attributes.get(key);
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        throw new ClassCastException("Attribute " + key + " is not of type " + type.getName());
    }
}
