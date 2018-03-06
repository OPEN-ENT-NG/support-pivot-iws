package fr.openent.supportpivot.service;

import fr.wseduc.webutils.Either;
import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonObject;

/**
 * Created by colenot on 07/12/2017.
 *
 * Service to handle pivot information and send at the right place
 */
public interface DemandeService {

    /**
     * Add issue from IWS
     * Check every mandatory field is present in jsonPivot, then send for treatment
     * @param jsonPivot JSON object in PIVOT format
     */
    void addIWS(HttpServerRequest request, JsonObject jsonPivot, Handler<Either<String, JsonObject>> handler);

    /**
     * Add issue from ENT
     * - Check every mandatory field is present in jsonPivot, then send for treatment
     * - Replace empty values by default ones
     * - Replace modules names
     * @param jsonPivot JSON object in PIVOT format
     */
    void addENT(JsonObject jsonPivot, Handler<Either<String, JsonObject>> handler);

    /**
     * Send an issue to IWS with fictive info, for testing purpose
     * @param mailTo mail to send to
     */
    void testMailToIWS(HttpServerRequest request, String mailTo, Handler<Either<String, JsonObject>> handler);

    /**
     * Send updated informations from a Jira ticket to IWS
     * @param idJira idJira updated in Jira to sens to IWS
     */
    void updateJiraToIWS(HttpServerRequest request, String idJira, Handler<Either<String, JsonObject>> handler);
}
