package fr.openent.supportpivot.services.routers;

import fr.openent.supportpivot.deprecatedservices.DefaultDemandeServiceImpl;
import fr.openent.supportpivot.deprecatedservices.DefaultJiraServiceImpl;
import fr.openent.supportpivot.model.endpoint.Endpoint;
import fr.openent.supportpivot.model.endpoint.EndpointFactory;
import fr.openent.supportpivot.model.ticket.PivotTicket;
import fr.openent.supportpivot.services.GlpiService;
import fr.openent.supportpivot.services.HttpClientService;
import fr.openent.supportpivot.services.MongoService;
import fr.openent.supportpivot.services.RouterService;
import io.vertx.core.*;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class CrnaRouterService implements RouterService {

    private Endpoint glpiEndpoint;
    private Endpoint jiraEndpoint;
    private Endpoint pivotEndpoint;

    private MongoService mongoService;
    protected static final Logger log = LoggerFactory.getLogger(CrnaRouterService.class);


    public CrnaRouterService(HttpClientService httpClientService, DefaultDemandeServiceImpl demandeService, DefaultJiraServiceImpl jiraService, GlpiService glpiService, MongoService mongoService, Vertx vertx) {
        EndpointFactory endpointFactory = new EndpointFactory(httpClientService, demandeService, jiraService, glpiService, vertx);
        this.mongoService = mongoService;

        glpiEndpoint = endpointFactory.getGlpiEndpoint();
        // jiraEndpoint = endpointFactory.getJiraEndpoint();
        pivotEndpoint = endpointFactory.getPivotEndpoint();
    }

    @Override
    public void dispatchTicket(String source, PivotTicket ticket, Handler<AsyncResult<PivotTicket>> handler) {
        switch (source) {
            case Endpoint.ENDPOINT_ENT:
                glpiEndpoint.send(ticket, result -> {
                    if (result.succeeded()) {
                        handler.handle(Future.succeededFuture(result.result()));

                        pivotEndpoint.send(ticket, resultEnt -> {
                            if (resultEnt.failed()) {
                                log.error("Ticket have not returned to ENT. " + resultEnt.cause().getMessage());
                            }
                        });
                        // add if result.result() failed
                    } else {
                        handler.handle(Future.failedFuture("sending ticket from ENT to GLPI failed: " + result.cause().getMessage()));
                    }
                });
                break;
            case Endpoint.ENDPOINT_GLPI:

                jiraEndpoint.send(ticket, result -> {
                    if (result.succeeded()) {
                        handler.handle(Future.succeededFuture(result.result()));
                        //TODO update ent id_jira.
                    } else {
                        handler.handle(Future.failedFuture("sending ticket from GLPI to JIRA failed: " + result.cause().getMessage()));
                    }
                });
            case Endpoint.ENDPOINT_JIRA:
                log.info("source JIRA");
            default:
                handler.handle(Future.failedFuture("unknown source"));
        }
    }

    @Override
    public void processTicket(String source, JsonObject ticketdata, Handler<AsyncResult<JsonObject>> handler) {
        this.mongoService.saveTicket(source, ticketdata);
        if (Endpoint.ENDPOINT_ENT.equals(source)) {
            pivotEndpoint.process(ticketdata, result -> {
                if (result.succeeded()) {
                    this.dispatchTicket(source, result.result(), dispatchHandler -> {
                        if (dispatchHandler.failed()) {
                            String message = "Dispatch ticket failed" + dispatchHandler.cause().getMessage();
                            log.error(message);
                            handler.handle(Future.failedFuture(message));
                        } else {
                            log.warn("ENT ticket " + dispatchHandler.result().getId() + " scaled into GLPI (ticket " + dispatchHandler.result().getGlpiId() + ")");
                            handler.handle(Future.succeededFuture(dispatchHandler.result().getJsonTicket()));
                        }
                    });
                } else {
                    log.error("Ticket has not been received from ENT: " + result.cause().getMessage(), (Object) result.cause().getStackTrace());
                }
            });
        } else if(Endpoint.ENDPOINT_JIRA.equals(source)) {
            jiraEndpoint.process(ticketdata, result -> {
                if (result.succeeded()) {
                    this.dispatchTicket(source, result.result(), dispatchHandler -> {
                        if (dispatchHandler.failed()) {
                            String message = "Dispatch ticket failed" + dispatchHandler.cause().getMessage();
                            log.error(message);
                            handler.handle(Future.failedFuture(message));
                        } else {
                            log.warn("ENT ticket " + dispatchHandler.result().getId() + " scaled into GLPI (ticket " + dispatchHandler.result().getGlpiId() + ")");
                            handler.handle(Future.succeededFuture(dispatchHandler.result().getJsonTicket()));
                        }
                    });
                } else {
                    log.error("Ticket has not been received from ENT: " + result.cause().getMessage(), (Object) result.cause().getStackTrace());
                }
            });
        } else {
            handler.handle(Future.failedFuture("Cannot process ticket due to an unknown source provenance."));
        }
    }

    @Override
    public void triggerTicket(String source, JsonObject data, Handler<AsyncResult<JsonObject>> handler) {
//        this.mongoService.saveTicket(source, data);
        if (Endpoint.ENDPOINT_GLPI.equals(source)) {
            // traitement du ticket glpi
            glpiEndpoint.trigger(data, result -> {
                if (result.succeeded()) {
                    handler.handle(Future.succeededFuture(JsonObject.mapFrom(result.result())));
                    List<PivotTicket> listTicket = result.result();
                    for (PivotTicket ticket : listTicket) {
                        dispatchTicket(source, ticket, dispatchResult -> {
                            if (dispatchResult.failed()) {
                                log.error("Dispatch failed " + dispatchResult.cause().getMessage(), ticket);
                            } else {
                                log.info("dispatch succeeded");
                            }
                        });
                        // check result for return
                    }
                } else {
                    handler.handle(Future.failedFuture("ticket creation failed"));
                }
            });
        } else {
            handler.handle(Future.failedFuture("unknown source"));
        }
    }

    private JsonObject convertListPivotTicketToJsonObject(List<PivotTicket> pivotTickets) {
        JsonObject jsonPivotTickets = new JsonObject();
        for (int i = 0; i < pivotTickets.size(); i++) {
            jsonPivotTickets.put(String.valueOf(i), pivotTickets.get(i).getJsonTicket());
        }
        return jsonPivotTickets;
    }

}
