package fr.openent.supportpivot.services.routers;

import fr.openent.supportpivot.constants.JiraConstants;
import fr.openent.supportpivot.constants.PivotConstants.SOURCES;
import fr.openent.supportpivot.helpers.JsonObjectSafe;
import fr.openent.supportpivot.model.endpoint.EndpointFactory;
import fr.openent.supportpivot.model.endpoint.jira.JiraEndpoint;
import fr.openent.supportpivot.model.endpoint.LdeEndPoint;
import fr.openent.supportpivot.model.ticket.PivotTicket;
import fr.openent.supportpivot.services.HttpClientService;
import fr.openent.supportpivot.services.JiraService;
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

    //todo split in two functions ?
    @Override
    public void readTickets(String source, JsonObject data, Handler<AsyncResult<JsonArray>> handler) {
        if (SOURCES.LDE.equals(source)) {
            String type = data == null ? "list" : data.getString("type", "");
            String minDate = data == null ? null : data.getString("date");
            if (type.equals("list")) {
                getTicketListFromJira( minDate, jiraResult -> {
                    if(jiraResult.failed()) {
                        handler.handle(Future.failedFuture(jiraResult.cause()));
                    } else {
                        ldeEndpoint.prepareJsonList(jiraResult.result(), handler);
                    }
                });
            } else {
                ldeEndpoint.process(data, ldeEndpointProcessResult -> {
                    if (ldeEndpointProcessResult.succeeded()) {
                        JsonObject pivotTicket = ldeEndpointProcessResult.result().getJsonTicket();
                        jiraEndpoint.process(pivotTicket, jiraEndpointProcessResult -> {
                            if (jiraEndpointProcessResult.succeeded()) {
                                PivotTicket pivotFormatTicket = jiraEndpointProcessResult.result();
                                ldeEndpoint.sendBack(pivotFormatTicket, ldeFormatTicketResult -> {    //TODO ici cette méthode devrait servir de conversion mais le type de retour n'est pas bon
                                    if (ldeFormatTicketResult.succeeded()) {
                                        handler.handle(Future.succeededFuture(new JsonArray().add(ldeFormatTicketResult.result())));
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

    private void getTicketListFromJira(String minDate, Handler<AsyncResult<List<PivotTicket>>> handler) {
        JsonObjectSafe data = new JsonObjectSafe();
        data.put(JiraConstants.ATTRIBUTION_FILTERNAME, JiraConstants.ATTRIBUTION_FILTER_LDE);
        data.putSafe(JiraConstants.ATTRIBUTION_FILTER_DATE, minDate);
        jiraEndpoint.trigger(data, handler);
    }

}
