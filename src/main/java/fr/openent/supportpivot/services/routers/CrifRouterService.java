package fr.openent.supportpivot.services.routers;

import fr.openent.supportpivot.constants.PivotConstants.SOURCES;
import fr.openent.supportpivot.deprecatedservices.DefaultDemandeServiceImpl;
import fr.openent.supportpivot.model.endpoint.EndpointFactory;
import fr.openent.supportpivot.model.endpoint.JiraEndpoint;
import fr.openent.supportpivot.model.endpoint.LdeEndPoint;
import fr.openent.supportpivot.model.ticket.PivotTicket;
import fr.openent.supportpivot.services.HttpClientService;
import fr.openent.supportpivot.services.JiraService;
import fr.openent.supportpivot.services.JiraServiceImpl;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.List;

public class CrifRouterService extends AbstractRouterService {




    private JiraEndpoint jiraEndpoint;
    private LdeEndPoint ldeEndpoint;

    public CrifRouterService(HttpClientService httpClientService, JiraService jiraService) {
        jiraEndpoint = EndpointFactory.getJiraEndpoint(httpClientService, jiraService);
        ldeEndpoint = EndpointFactory.getLdeEndpoint();
    }

    @Override
    public void dispatchTicket(String source, PivotTicket ticket, Handler<AsyncResult<PivotTicket>> handler) {
        if (SOURCES.LDE.equals(source)) {
            jiraEndpoint.send(ticket, jiraEndpointSendResult -> {
                if (jiraEndpointSendResult.succeeded()) {
                    handler.handle(Future.succeededFuture(jiraEndpointSendResult.result()));
                } else {
                    handler.handle(Future.failedFuture(jiraEndpointSendResult.cause()));
                }

            });
        } else {
            handler.handle(Future.failedFuture(source + " is an unsupported value for IDF router."));
        }
    }

    @Override
    public void processTicket(String source, JsonObject ticketdata, Handler<AsyncResult<JsonObject>> handler) {
        if (SOURCES.LDE.equals(source)) {
            ldeEndpoint.process(ticketdata, ldeEndpointProcessResult -> {
                if (ldeEndpointProcessResult.succeeded()) {
                    dispatchTicket(source, ldeEndpointProcessResult.result(), dispatchResult -> {
                        if (dispatchResult.succeeded()) {
                            handler.handle(Future.succeededFuture(dispatchResult.result().getJsonTicket()));
                        } else {
                            handler.handle(Future.failedFuture(dispatchResult.cause()));
                        }
                    });
                } else {
                    handler.handle(Future.failedFuture(ldeEndpointProcessResult.cause()));
                }

            });
        } else {
            handler.handle(Future.failedFuture(source + " is an unsupported value for IDF router."));
        }
    }

    @Override
    public void readTickets(String source, JsonObject data, Handler<AsyncResult<JsonArray>> handler) {
        if (SOURCES.LDE.equals(source)) {
            if (data == null) {
                data = new JsonObject();
                data.put(jiraEndpoint.ATTRIBUTION_FILTERNAME, "LDE");
                jiraEndpoint.trigger(data, jiraEndpointTriggerResult -> {
                    if (jiraEndpointTriggerResult.succeeded()) {
                        handler.handle(Future.succeededFuture(convertListPivotTicketToJsonObject(jiraEndpointTriggerResult.result())));
                    } else {
                        handler.handle(Future.failedFuture(jiraEndpointTriggerResult.cause()));
                    }

                });
            } else {
                ldeEndpoint.process(data, ldeEndpointProcessResult -> {
                    if (ldeEndpointProcessResult.succeeded()) {
                        JsonObject pivotTicket = ldeEndpointProcessResult.result().getJsonTicket();
                        jiraEndpoint.process(pivotTicket, jiraEndpointProcessResult -> {
                            if (jiraEndpointProcessResult.succeeded()) {
                                PivotTicket pivotFormatTicket = jiraEndpointProcessResult.result();
                                ldeEndpoint.send(pivotFormatTicket, ldeFormatTicketResult -> {    //TODO ici cette méthode devrait servir de conversion mais le type de retour n'est pas bon
                                    if (ldeFormatTicketResult.succeeded()) {
                                        handler.handle(Future.succeededFuture(new JsonArray().add(ldeFormatTicketResult.result().getJsonTicket())));
                                    } else {
                                        handler.handle(Future.failedFuture(ldeFormatTicketResult.cause()));
                                    }
                                });
                            } else {
                                handler.handle(Future.failedFuture(jiraEndpointProcessResult.cause()));
                            }
                        });
                    } else {
                        handler.handle(Future.failedFuture(ldeEndpointProcessResult.cause()));
                    }
                });
            }
        } else {
            handler.handle(Future.failedFuture(source + " is an unsupported value for IDF router."));
        }
    }

    private JsonArray convertListPivotTicketToJsonObject(List<PivotTicket> pivotTickets) {
        JsonArray jsonPivotTickets = new JsonArray();
        for (PivotTicket pivotTicket : pivotTickets) {
            jsonPivotTickets.add(pivotTicket.getJsonTicket());
        }
        return jsonPivotTickets;
    }

}
