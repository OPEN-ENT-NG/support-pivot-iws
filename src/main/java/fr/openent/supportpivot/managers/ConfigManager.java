package fr.openent.supportpivot.managers;

import fr.openent.supportpivot.Supportpivot;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class ConfigManager {

    private final String defaultCollectivity;
    private final String mongoCollection;
    private final String jiraHost;
    private final String jiraBaseUri;
    private final String glpiHost;
    private final String glpiRootUri;
    private final String glpiLogin;
    private final String glpiPassword;
    private final String glpiCategory;
    private final String glpiType;
    private final JsonObject glpiLocation;
    private final String jiraProjectKey;
    private final JsonObject customFields;

    private final JsonObject rawConfig;

    private static final Logger log = LoggerFactory.getLogger(Supportpivot.class);


    public ConfigManager(JsonObject config) {
        this.rawConfig = config;

        this.defaultCollectivity = config.getString("collectivity", "CRNA");
        // Keep default value for backward compatibility
        this.mongoCollection = config.getString("mongo-collection", "support.demandes");

        JsonObject configGlpi = config.getJsonObject("glpi");
        this.glpiHost = configGlpi.getString("host");
        this.glpiRootUri = configGlpi.getString("root-uri");

        this.glpiLogin = configGlpi.getString("login");
        this.glpiPassword = configGlpi.getString("password");

        //this.glpiCategory = this.toHashMapCategories(configGlpi.getString("mapping.category"));

        JsonObject glpiMappingConf = configGlpi.getJsonObject("mapping");
        this.glpiCategory = glpiMappingConf.getString("default_category");
        this.glpiType = glpiMappingConf.getString("default_type");
        this.glpiLocation = glpiMappingConf.getJsonObject("location");

        this.jiraHost = config.getString("jira-host");
        this.jiraBaseUri = config.getString("jira-base-uri");

        this.jiraProjectKey = config.getString("jira-project-key");

        this.customFields = config.getJsonObject("jira-custom-fields");

        if(defaultCollectivity.isEmpty()) {
            log.warn("Default collectivity absent from configuration");
        }

        /*if(jiraHost.isEmpty()) {
            log.warn("Jira host absent from configuration");
        }

        if(jiraBaseUri.isEmpty()) {
            log.warn("Jira base URI absent from configuration");
        }*/
    }

    /*public void toHashMapCategories(JsonArray arrayCategories, ) {

    }*/

    public String getDefaultCollectivity() { return defaultCollectivity; }
    public String getMongoCollection() { return mongoCollection; }
    public JsonObject getRawConfig() { return rawConfig; }

    public String getGlpiHost() { return glpiHost; }
    public String getGlpiRootUri() { return glpiRootUri; }
    public String getGlpiUri() { return glpiHost + glpiRootUri; }
    public String getGlpiLogin() { return glpiLogin; }
    public String getGlpiPassword() { return glpiPassword; }
    public String getGlpiCategory() { return glpiCategory; }
    public String getGlpiType() { return glpiType; }
    public JsonObject getGlpiLocation() { return glpiLocation; }

    public String getJiraHost() { return jiraHost; }
    public String getJiraBaseUri() { return jiraBaseUri; }
    public String getJiraBaseUrl() { return jiraHost + jiraBaseUri; }
    public String getJiraProjectKey() { return jiraProjectKey; }
    public JsonObject getCustomFields() { return customFields; }
}
