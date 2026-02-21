package com.atlasia.ai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Single-row global settings store.
 * Each row holds a named JSON blob; the key "oversight_config" holds
 * the current OversightConfig serialised via Jackson.
 */
@Entity
@Table(name = "ai_global_settings")
public class GlobalSettingsEntity {

    @Id
    @Column(name = "setting_key", length = 100)
    private String settingKey;

    @Column(name = "value_json", nullable = false, columnDefinition = "text")
    private String valueJson;

    protected GlobalSettingsEntity() {}

    public GlobalSettingsEntity(String settingKey, String valueJson) {
        this.settingKey = settingKey;
        this.valueJson  = valueJson;
    }

    public String getSettingKey()             { return settingKey; }
    public String getValueJson()              { return valueJson; }
    public void   setValueJson(String json)   { this.valueJson = json; }
}
