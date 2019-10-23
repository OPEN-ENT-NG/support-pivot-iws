package fr.openent.supportpivot.model.endpoint;

import fr.openent.supportpivot.constants.PivotConstants;
import fr.openent.supportpivot.model.ticket.PivotTicket;
import fr.openent.supportpivot.services.GlpiService;
import io.vertx.core.*;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static fr.wseduc.webutils.Server.getEventBus;
import static fr.wseduc.webutils.Utils.handlerToAsyncHandler;


class PivotEndpoint implements Endpoint {

    private EventBus eventBus;
    private GlpiService glpiService;

    private static final Logger log = LoggerFactory.getLogger(PivotEndpoint.class);

    PivotEndpoint(Vertx vertx, GlpiService glpiService) {
        this.eventBus = getEventBus(vertx);
        this.glpiService =  glpiService;
    }

    @Override
    public void trigger(JsonObject data, Handler<AsyncResult<List<PivotTicket>>> handler) {
    }

    @Override
    public void process(JsonObject ticketData, Handler<AsyncResult<PivotTicket>> handler) {
        final JsonObject issue = ticketData.getJsonObject("issue");
        PivotTicket ticket = new PivotTicket();
        ticket.setJsonObject(issue);
        if (ticket.getIwsId() != null) {
            ticket.setGlpiId(ticket.getIwsId().trim());
        }
        handler.handle(Future.succeededFuture(ticket));
    }

    @Override
    public void send(PivotTicket ticket, Handler<AsyncResult<PivotTicket>> handler) {

        String ticketId = ticket.getId();

        List<Future> futures = new ArrayList<>();
        if (ticketId == null || ticketId.isEmpty()) {
            Future<Boolean> future = Future.future();
            futures.add(future);
            this.glpiService.getTicket(ticket.getGlpiId(), result -> {
                if (result.succeeded()) {
                    this.glpiService.getIdFromGlpiTicket(result.result(), resultId -> {
                        if (result.succeeded()) {
                            ticket.setId(resultId.result());
                            future.complete();
                        } else {
                            future.fail("An error occurred while: " + PivotConstants.ID_FIELD + ". " + result.cause().getMessage());
                        }
                    });

                } else {
                    future.fail("Field mandatory: " + PivotConstants.ID_FIELD + ". " + result.cause().getMessage());
                }
            });
            return;
        }

        CompositeFuture.all(futures).setHandler(event -> {
            if (event.succeeded()) {
                try {
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
                } catch (Error e) {
                    handler.handle(Future.failedFuture(e.getMessage()));
                }
            } else {
                handler.handle(Future.failedFuture(event.cause().getCause()));
            }
        });
    }
}
