package fr.openent.supportpivot.model.endpoint;

import fr.openent.supportpivot.constants.PivotConstants;
import fr.openent.supportpivot.model.ticket.PivotTicket;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.List;

import static fr.wseduc.webutils.Server.getEventBus;
import static fr.wseduc.webutils.Utils.handlerToAsyncHandler;


class PivotEndpoint implements Endpoint {

    private EventBus eventBus;

    private static final Logger log = LoggerFactory.getLogger(PivotEndpoint.class);

    PivotEndpoint(Vertx vertx) {
        this.eventBus = getEventBus(vertx);
    }

    @Override
    public void trigger(JsonObject data, Handler<AsyncResult<List<PivotTicket>>> handler) {
    }

    @Override
    public void process(JsonObject ticketData, Handler<AsyncResult<PivotTicket>> handler) {
        final JsonObject issue = ticketData.getJsonObject("issue");
        PivotTicket ticket = new PivotTicket();
        ticket.setJsonObject(issue);
        handler.handle(Future.succeededFuture(ticket));
    }

    @Override
    public void send(PivotTicket ticket, Handler<AsyncResult<PivotTicket>> handler) {

        String ticketId = ticket.getId();

        if (ticketId == null || ticketId.isEmpty()) {
            handler.handle(Future.failedFuture("Field mandatory: " + PivotConstants.ID_FIELD));
            return;
        }

        eventBus
                .send(PivotConstants.BUS_SEND, new JsonObject()
                                .put("action", "create")
                                .put("issue", ticket.getJsonTicket()),
                        handlerToAsyncHandler(message -> {
                            if (PivotConstants.ENT_BUS_OK_STATUS.equals(message.body().getString("status"))) {
                                log.info(message.body());
                                handler.handle(Future.succeededFuture(new PivotTicket()));
                            } else {
                                handler.handle(Future.failedFuture(message.body().toString()));
                            }
                        })
                );
    }
}
