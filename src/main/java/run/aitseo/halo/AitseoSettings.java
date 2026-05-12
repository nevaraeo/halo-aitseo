package run.aitseo.halo;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Settings group "basic" - mirrors src/main/resources/extensions/setting.yaml.
 */
public class AitseoSettings {

    @JsonProperty("connectionKey")
    private String connectionKey = "";

    @JsonProperty("defaultCategory")
    private String defaultCategory = "";

    @JsonProperty("defaultTags")
    private String defaultTags = "";

    @JsonProperty("publishImmediately")
    private String publishImmediately = "true";

    @JsonProperty("publishOwner")
    private String publishOwner = "admin";

    public String getConnectionKey() { return connectionKey == null ? "" : connectionKey; }
    public void setConnectionKey(String v) { this.connectionKey = v; }

    public String getDefaultCategory() { return defaultCategory == null ? "" : defaultCategory; }
    public void setDefaultCategory(String v) { this.defaultCategory = v; }

    public String getDefaultTags() { return defaultTags == null ? "" : defaultTags; }
    public void setDefaultTags(String v) { this.defaultTags = v; }

    public String getPublishImmediately() { return publishImmediately == null ? "true" : publishImmediately; }
    public void setPublishImmediately(String v) { this.publishImmediately = v; }

    public String getPublishOwner() {
        return (publishOwner == null || publishOwner.isBlank()) ? "admin" : publishOwner;
    }
    public void setPublishOwner(String v) { this.publishOwner = v; }

    public boolean shouldPublishImmediately() {
        return "true".equalsIgnoreCase(getPublishImmediately());
    }
}
