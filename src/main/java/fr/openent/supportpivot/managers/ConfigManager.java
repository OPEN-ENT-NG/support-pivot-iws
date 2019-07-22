package fr.openent.supportpivot.managers;

import fr.openent.supportpivot.Supportpivot;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

public class ConfigManager {

    private final String defaultCollectivity;
    private final String mongoCollection;

    private static final Logger log = LoggerFactory.getLogger(Supportpivot.class);

    public ConfigManager(JsonObject config) {

        this.defaultCollectivity = config.getString("collectivity", "");
        // Keep default value for backward compatibility
        this.mongoCollection = config.getString("mongo-collection", "support.demandes");

        if(defaultCollectivity.isEmpty()) {
            log.warn("Default collectivity absent from configuration");
        }
    }

    public String getDefaultCollectivity() { return defaultCollectivity; }
    public String getMongoCollection() { return mongoCollection; }
}
