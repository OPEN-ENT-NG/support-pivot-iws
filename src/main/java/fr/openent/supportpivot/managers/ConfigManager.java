package fr.openent.supportpivot.managers;

import fr.openent.supportpivot.Supportpivot;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class ConfigManager {

    private static JsonObject config;

    private final String collectivity;
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
    private final String externalEndpointActivated;


    private final JsonObject jiraCustomFields;
    private final String jiraCustomFieldIdForExternalId;
    private final String jiraDefaultStatus;
    private final JsonObject jiraStatusMapping;

    private final String glpiSupportCGIUsername;

    private static final Logger log = LoggerFactory.getLogger(Supportpivot.class);
    private final String synchroCronDate;


    private ConfigManager() {

        collectivity = config.getString("collectivity");
        if(collectivity.isEmpty()) {
            log.warn("Default collectivity absent from configuration");
        }
        mongoCollection = config.getString("mongo-collection", "support.demandes");
        proxyHost = config.getString("proxy-host", null);
        proxyPort = config.getInteger("proxy-port");
        //GLPI Configuration
        JsonObject configGlpi = config.getJsonObject("glpi-endpoint");
        if(configGlpi != null){
            externalEndpointActivated = "GLPI";
            glpiHost = configGlpi.getString("host");
            CheckUrlSyntax(glpiHost, "glpi-endpoint/host");
            glpiRootUri = configGlpi.getString("root-uri");
            glpiLogin = configGlpi.getString("login");
            glpiPassword = configGlpi.getString("password");
            glpiSupportCGIUsername = configGlpi.getString("supportcgi_username");
            //glpiCategory = toHashMapCategories(configGlpi.getString("mapping.category"));
            synchroCronDate = configGlpi.getString("synchro-cron");

            JsonObject glpiMappingConf = configGlpi.getJsonObject("mapping");
            glpiCategory = glpiMappingConf.getString("default_category");
            glpiType = glpiMappingConf.getString("default_type");
            glpiLocation = glpiMappingConf.getJsonObject("location");

        } else {
            glpiHost = null;
            glpiRootUri = null;
            glpiLogin = null;
            glpiPassword = null;
            glpiSupportCGIUsername = null;
            glpiCategory = null;
            glpiType = null;
            glpiLocation = null;
            synchroCronDate = null;
            JsonObject configIws = config.getJsonObject("iws-endpoint");
            if(configIws != null){
                externalEndpointActivated = "IWS";
            } else {
                externalEndpointActivated = "NONE";
            }

        }




        //JIRA configuration
        jiraHost = config.getString("jira-host").trim() ;
        CheckUrlSyntax(jiraHost, "jira-host");
        jiraBaseUri = config.getString("jira-base-uri");
        jiraProjectKey = config.getString("jira-project-key");
        jiraLogin = config.getString("jira-login");
        jiraPassword = config.getString("jira-passwd");
        jiraCustomFields = config.getJsonObject("jira-custom-fields");

        if(jiraCustomFields.containsKey("id_external")) {
            jiraCustomFieldIdForExternalId = jiraCustomFields.getString("id_external");
        }else{
            //For retro-compatibility
            jiraCustomFieldIdForExternalId = jiraCustomFields.getString("id_iws");
        }
        jiraStatusMapping = config.getJsonObject("jira-status-mapping").getJsonObject("statutsJira");
        jiraDefaultStatus = config.getJsonObject("jira-status-mapping").getString("statutsDefault");


    }

    private static void CheckUrlSyntax(String URLvalue, String parameterName) {
        try {
            new URL(URLvalue).toURI();
        }catch (Exception e) {
            log.error("entcore.json : parameter " + parameterName+" is not a valid URL",e);
        }
    }


    public String getCollectivity() { return collectivity; }
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
    public String getSynchroCronDate() { return synchroCronDate;   }

    public String getJiraHost() { return jiraHost; }
    public String getJiraBaseUri() { return jiraBaseUri; }
    public URI getJiraBaseUrl() {
        try {
            return new URI(jiraHost).resolve(jiraBaseUri);
        } catch (URISyntaxException e) {
            log.fatal("bad URL jira-host / jira-base-uri :" +  jiraHost + " / " + jiraBaseUri);
            return URI.create("");
        }
    }
    public String getJiraLogin() { return jiraLogin; }
    public String getJiraPassword() { return jiraPassword; }
    public String getJiraAuthInfo() { return jiraLogin + ":" + jiraPassword; }
    public String getJiraProjectKey() { return jiraProjectKey; }
    public JsonObject getJiraCustomFields() { return jiraCustomFields; }
    public String getJiraCustomFieldIdForExternalId() { return jiraCustomFieldIdForExternalId; }
    public String getExternalEndpointActivated() { return externalEndpointActivated; }
    public JsonObject getJiraStatusMapping() { return jiraStatusMapping; }
    public String getJiraDefaultStatus() { return jiraDefaultStatus; }


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
