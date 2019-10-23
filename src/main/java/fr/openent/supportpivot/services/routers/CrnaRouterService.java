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
        jiraEndpoint = endpointFactory.getJiraEndpoint();
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
                    } else {
                        handler.handle(Future.failedFuture("sending ticket from ENT to GLPI failed: " + result.cause().getMessage()));
                    }
                });
                break;
            case Endpoint.ENDPOINT_GLPI:
                Future jiraFuture = Future.future();
                jiraEndpoint.send(ticket, result -> {
                    if (result.succeeded()) {
                        jiraFuture.complete(result.result());
                    } else {
                        jiraFuture.fail("sending ticket from GLPI to JIRA failed: " + result.cause().getMessage());
                    }
                });

                Future entFuture = Future.future();
                pivotEndpoint.send(ticket, result -> {
                    if (result.succeeded()) {
                        entFuture.complete(result.result());
                    } else {
                        entFuture.fail("sending ticket from GLPI to ENT failed: " + result.cause().getMessage());
                    }
                });
                CompositeFuture.all(jiraFuture, entFuture).setHandler(event -> {
                    if (event.succeeded()) {
                        log.info("ticket id_glpi: " + ticket.getGlpiId() + " scaled");
                        handler.handle(Future.succeededFuture(ticket));
                    } else {
                        String message = "ticket id_glpi: " + ticket.getGlpiId() + " can not be scaled: " + event.cause().getMessage();
                        log.error(message);
                        handler.handle(Future.failedFuture(message));
                    }
                });
                break;
            case Endpoint.ENDPOINT_JIRA:
                glpiEndpoint.send(ticket, result -> {
                    if (result.succeeded()) {
                        pivotEndpoint.send(ticket, resultPivot -> {
                            if (resultPivot.succeeded()) {
                                handler.handle(Future.succeededFuture(resultPivot.result()));
                            } else {
                                handler.handle(Future.failedFuture("sending ticket from Jira to GLPI and then to ENT failed: " + resultPivot.cause().getMessage()));
                            }
                        });
                    } else {
                        handler.handle(Future.failedFuture("sending ticket from Jira to GLPI failed: " + result.cause().getMessage()));
                    }
                });
                break;
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
        } else if (Endpoint.ENDPOINT_JIRA.equals(source)) {
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
                    handler.handle(Future.succeededFuture(convertListPivotTicketToJsonObject(result.result())));
                    List<PivotTicket> listTicket = result.result();
                    List<Future> futures = new ArrayList<>();
                    for (PivotTicket ticket : listTicket) {
                        Future<PivotTicket> future = Future.future();
                        futures.add(future);
                        this.dispatchTicket(source, ticket, dispatchResult -> {
                            if (dispatchResult.failed()) {
                                future.fail("Dispatch failed " + dispatchResult.cause().getMessage());
                            } else {
                                future.complete(dispatchResult.result());
                            }
                        });
                        // check result for return
                    }
                    CompositeFuture.join(futures).setHandler(event -> {
                        if (event.succeeded()) {
                            log.info("Dispatch Glpi ticket to jira succeed");
                        } else {
                            log.info("Dispatch Glpi ticket to jira failed: " + event.cause().getMessage());
                        }
                    });
                } else {
                    handler.handle(Future.failedFuture("ticket creation failed: " + result.cause().getMessage()));
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
