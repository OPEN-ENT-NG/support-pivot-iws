package fr.openent.supportpivot.services;

import fr.openent.supportpivot.model.ticket.PivotTicket;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.List;

public interface RouterService {


    void dispatchTicket(String source, PivotTicket ticket, Handler<AsyncResult<PivotTicket>> handler);

    //fonctions publiques prendre ticket + endpoint+ contenu ticket

    void processTicket(String source, JsonObject ticketdata,
                      Handler<AsyncResult<JsonObject>> handler);

    void triggerTicket(String source, JsonObject data,
                       Handler<AsyncResult<JsonObject>> handler);

    void readTickets(String source, JsonObject data, Handler<AsyncResult<JsonArray>> handler);
}
