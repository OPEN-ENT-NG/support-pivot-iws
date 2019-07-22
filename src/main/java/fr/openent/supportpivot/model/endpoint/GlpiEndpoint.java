package fr.openent.supportpivot.model.endpoint;

import fr.openent.supportpivot.model.ticket.Ticket;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

class GlpiEndpoint implements Endpoint {
    @Override
    public void trigger(JsonObject data, Handler<AsyncResult<JsonObject>> handler) {

    }

    @Override
    public void process(JsonObject ticketData, Handler<AsyncResult<Ticket>> handler) {

    }

    @Override
    public void send(Ticket ticket, Handler<AsyncResult<JsonObject>> handler) {

    }
}
