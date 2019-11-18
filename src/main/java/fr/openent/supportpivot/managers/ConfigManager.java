package fr.openent.supportpivot.managers;

import fr.openent.supportpivot.Supportpivot;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class ConfigManager {

    private static JsonObject config;

    private final String defaultCollectivity;
    private final String mongoCollection;
    private final String proxyHost;
    private final Integer proxyPort;
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
    private final String jiraLogin;
    private final String jiraPassword;
    private final JsonObject customFields;
    private final String glpiSupportCGIUsername;

    private static final Logger log = LoggerFactory.getLogger(Supportpivot.class);


    private ConfigManager() {

        this.defaultCollectivity = config.getString("collectivity");
        if(defaultCollectivity.isEmpty()) {
            log.warn("Default collectivity absent from configuration");
        }
        this.mongoCollection = config.getString("mongo-collection", "support.demandes");
        this.proxyHost = config.getString("proxy-host", null);
        this.proxyPort = config.getInteger("proxy-port");

        //GLPI Configuration
        JsonObject configGlpi = config.getJsonObject("glpi");
        if(configGlpi != null){
            this.glpiHost = configGlpi.getString("host");
            this.glpiRootUri = configGlpi.getString("root-uri");
            this.glpiLogin = configGlpi.getString("login");
            this.glpiPassword = configGlpi.getString("password");
            this.glpiSupportCGIUsername = configGlpi.getString("supportcgi_username");
            //this.glpiCategory = this.toHashMapCategories(configGlpi.getString("mapping.category"));
            JsonObject glpiMappingConf = configGlpi.getJsonObject("mapping");
            this.glpiCategory = glpiMappingConf.getString("default_category");
            this.glpiType = glpiMappingConf.getString("default_type");
            this.glpiLocation = glpiMappingConf.getJsonObject("location");
        } else {
            this.glpiHost = null;
            this.glpiRootUri = null;
            this.glpiLogin = null;
            this.glpiPassword = null;
            this.glpiSupportCGIUsername = null;
            this.glpiCategory = null;
            this.glpiType = null;
            this.glpiLocation = null;
        }



        //JIRA configuration
        this.jiraHost = config.getString("jira-host");
        this.jiraBaseUri = config.getString("jira-base-uri");
        this.jiraProjectKey = config.getString("jira-project-key");
        this.jiraLogin = config.getString("jira-login");
        this.jiraPassword = config.getString("jira-passwd");
        this.customFields = config.getJsonObject("jira-custom-fields");

    }


    public String getDefaultCollectivity() { return defaultCollectivity; }
    public String getMongoCollection() { return mongoCollection; }
    public String getProxyHost() { return proxyHost; }
    public Integer getProxyPort() { return proxyPort; }

    public String getGlpiHost() { return glpiHost; }
    public String getGlpiRootUri() { return glpiRootUri; }
    public String getGlpiUri() { return glpiHost + glpiRootUri; }
    public String getGlpiLogin() { return glpiLogin; }
    public String getGlpiPassword() { return glpiPassword; }
    public String getGlpiCategory() { return glpiCategory; }
    public String getGlpiType() { return glpiType; }
    public JsonObject getGlpiLocation() { return glpiLocation; }
    public String getGlpiSupportCGIUsername() { return glpiSupportCGIUsername; }

    public String getJiraHost() { return jiraHost; }
    public String getJiraBaseUri() { return jiraBaseUri; }
    public String getJiraBaseUrl() { return jiraHost + jiraBaseUri; }
    public String getJiraLogin() { return jiraLogin; }
    public String getJiraPassword() { return jiraPassword; }
    public String getJiraAuthInfo() { return jiraLogin + ":" + jiraPassword; }
    public String getJiraProjectKey() { return jiraProjectKey; }
    public JsonObject getCustomFields() { return customFields; }


    /** Holder */
    private static class Holder {
        private final static ConfigManager instance = new ConfigManager();
    }

    public static void init(JsonObject configuration) {
        config = configuration;
    }

    public static ConfigManager getInstance() {
        return Holder.instance;
    }
}
