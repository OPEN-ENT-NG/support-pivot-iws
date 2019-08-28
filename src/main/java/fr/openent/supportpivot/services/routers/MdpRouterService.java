package fr.openent.supportpivot.services.routers;

import fr.openent.supportpivot.model.ticket.PivotTicket;
import fr.openent.supportpivot.model.ticket.Ticket;
import fr.openent.supportpivot.services.RouterService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

public class MdpRouterService implements RouterService {
    @Override
    public void dispatchTicket(String source, PivotTicket ticket, Handler<AsyncResult<PivotTicket>> handler) {

    }

    @Override
    public void processTicket(String source, JsonObject ticketdata, Handler<AsyncResult<JsonObject>> handler) {

    }

    @Override
    public void triggerTicket(String source, JsonObject data, Handler<AsyncResult<JsonObject>> handler) {

    }
}
