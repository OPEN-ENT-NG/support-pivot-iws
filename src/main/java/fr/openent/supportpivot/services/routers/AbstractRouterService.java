package fr.openent.supportpivot.services.routers;

import fr.openent.supportpivot.model.ticket.PivotTicket;
import fr.openent.supportpivot.services.RouterService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public abstract class AbstractRouterService implements RouterService {
    @Override
    public void dispatchTicket(String source, PivotTicket ticket, Handler<AsyncResult<PivotTicket>> handler) {
        handler.handle(Future.failedFuture("dispatch ticket is unsupported for " + getClass().getName()));
    }

    @Override
    public void processTicket(String source, JsonObject ticketdata, Handler<AsyncResult<JsonObject>> handler) {
        handler.handle(Future.failedFuture("process ticket is unsupported for " + getClass().getName()));
    }

    @Override
    public void triggerTicket(String source, JsonObject data, Handler<AsyncResult<JsonObject>> handler) {
        handler.handle(Future.failedFuture("trigger ticket is unsupported for " + getClass().getName()));
    }

    @Override
    public void readTickets(String source, JsonObject data, Handler<AsyncResult<JsonArray>> handler) {
        handler.handle(Future.failedFuture("read tickets is unsupported for " + getClass().getName()));
    }
}
