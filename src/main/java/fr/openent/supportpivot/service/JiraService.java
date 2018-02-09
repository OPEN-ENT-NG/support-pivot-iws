package fr.openent.supportpivot.service;

import fr.wseduc.webutils.Either;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonObject;

/**
 * Created by mercierq on 09/02/2018.
 * Service to handle pivot information and send it to Jira
 */
public interface JiraService {
    /**
     * Add issue from IWS to JIRA
     * - Check every mandatory field is present in jsonPivot, then send for treatment
     * - Replace empty values by default ones
     * - Replace modules names
     * @param jsonPivot JSON object in PIVOT format
     */
    void sendToJIRA(JsonObject jsonPivot, final Handler<Either<String, JsonObject>> handler);
}
