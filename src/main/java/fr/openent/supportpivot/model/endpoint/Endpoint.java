package fr.openent.supportpivot.model.endpoint;

import fr.openent.supportpivot.model.ticket.PivotTicket;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

import java.util.List;

public interface Endpoint {

    static final String ENDPOINT_JIRA = "jira";
    static final String ENDPOINT_ENT = "ent";
    static final String ENDPOINT_IWS = "iws";
    static final String ENDPOINT_GLPI = "glpi";

    /**
     * Triggers ticket recuperation for this endpoint.
     * Might not do anything if the endpoint does not use trigger mecanism.
     * @param data Useful data for trigger. Might be an empty json object, but not null
     * @param handler Handler for callback
     */
    void trigger(JsonObject data, Handler<AsyncResult<List<PivotTicket>>> handler);

    /**
     * Process an incoming ticket from that endpoint.
     * @param ticketData Ticket data
     * @param handler Handler for callback
     */
    void process(JsonObject ticketData, Handler<AsyncResult<PivotTicket>> handler);

    /**
     * Process an existing ticket to send to that endpoint.
     * @param ticket Ticket data
     * @param handler Handler for callback
     */
    void send(PivotTicket ticket, Handler<AsyncResult<PivotTicket>> handler);
}
