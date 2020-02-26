package fr.openent.supportpivot.model.endpoint;

import fr.openent.supportpivot.model.ticket.PivotTicket;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

import java.util.List;

public class AbstractEndpoint implements Endpoint {
    @Override
    public void trigger(JsonObject data, Handler<AsyncResult<List<PivotTicket>>> handler) {
        handler.handle(Future.failedFuture("Trigger is not allowed for " + getClass().getName()));
    }

    @Override
    public void process(JsonObject ticketData, Handler<AsyncResult<PivotTicket>> handler) {
        handler.handle(Future.failedFuture("process is not allowed for " + getClass().getName()));
    }

    @Override
    public void send(PivotTicket ticket, Handler<AsyncResult<PivotTicket>> handler) {
        handler.handle(Future.failedFuture("Send is not allowed for " + getClass().getName()));
    }
}
