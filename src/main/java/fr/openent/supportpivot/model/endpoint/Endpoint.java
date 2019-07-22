package fr.openent.supportpivot.model.endpoint;

import com.sun.istack.internal.NotNull;
import fr.openent.supportpivot.model.ticket.Ticket;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

public interface Endpoint {

    /**
     * Triggers ticket recuperation for this endpoint.
     * Might not do anything if the endpoint does not use trigger mecanism.
     * @param data Useful data for trigger. Might be an empty json object, but not null
     * @param handler Handler for ballback
     */
    void trigger(@NotNull JsonObject data, @NotNull Handler<AsyncResult<JsonObject>> handler);

    /**
     * Process an incoming ticket from that endpoint.
     * @param ticketData Ticket data
     * @param handler Handler for ballback
     */
    void process(@NotNull JsonObject ticketData, @NotNull Handler<AsyncResult<Ticket>> handler);

    /**
     * Process an existing ticket to send to that endpoint.
     * @param ticket Ticket data
     * @param handler Handler for ballback
     */
    void send(@NotNull Ticket ticket, @NotNull Handler<AsyncResult<JsonObject>> handler);
}
