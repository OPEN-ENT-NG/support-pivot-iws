package fr.openent.supportpivot.services.routers;

import fr.openent.supportpivot.deprecatedservices.DemandeService;
import fr.openent.supportpivot.managers.ConfigManager;
import fr.openent.supportpivot.model.endpoint.Endpoint;
import fr.openent.supportpivot.model.endpoint.EndpointFactory;
import fr.openent.supportpivot.model.ticket.PivotTicket;
import fr.openent.supportpivot.model.ticket.Ticket;
import fr.openent.supportpivot.services.HttpClientService;
import fr.openent.supportpivot.services.RouterService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.List;

public class CrnaRouterService implements RouterService {

    private Endpoint glpiEndpoint;
    private Endpoint jiraEndpoint;
    private Endpoint pivotEndpoint;
    protected static final Logger log = LoggerFactory.getLogger(CrnaRouterService.class);


    public CrnaRouterService(HttpClientService httpClientService, DemandeService demandeService, ConfigManager config, Vertx vertx) {
        EndpointFactory endpointFactory = new EndpointFactory(config, httpClientService, demandeService, vertx);
        glpiEndpoint = endpointFactory.getGlpiEndpoint();
        // jiraEndpoint = endpointFactory.getJiraEndpoint();
        pivotEndpoint = endpointFactory.getPivotEndpoint();
    }

    @Override
    public void dispatchTicket(String source, PivotTicket ticket, Handler<AsyncResult<PivotTicket>> handler) {
        switch (source) {
            case Endpoint.ENDPOINT_ENT:
                log.info("source ENT");
                glpiEndpoint.send(ticket, result -> {
                    if (result.succeeded()) {
                        handler.handle(Future.succeededFuture(result.result()));

                        pivotEndpoint.send(ticket, resultEnt -> {
                        });

                        /*jiraEndpoint.send(ticket, jiraResult -> { TODO, when Glpi process end, send to jira.
                            if (jiraResult.succeeded()) {
                                log.info("resultat(jira)" + jiraResult.result(), jiraResult.result());
                                // add if result.result() failed
                            } else {
                                handler.handle(Future.failedFuture("sending ticket to GLPI failed: " + jiraResult.cause().getMessage()));
                            }
                        });*/
                        // add if result.result() failed
                    } else {
                        handler.handle(Future.failedFuture("sending ticket to GLPI failed: " + result.cause().getMessage()));
                    }
                });
                break;
            case Endpoint.ENDPOINT_GLPI:
                log.info("source GLPI");
                jiraEndpoint.send(ticket, result -> {
                    if (result.succeeded()) {
                        handler.handle(Future.succeededFuture(result.result()));
                    } else {
                        handler.handle(Future.failedFuture("ticket creation failed"));
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
        if (Endpoint.ENDPOINT_ENT.equals(source)) {
            pivotEndpoint.process(ticketdata, result -> {
                if (result.succeeded()) {
                    this.dispatchTicket(Endpoint.ENDPOINT_ENT, result.result(), dispatchHandler -> {
                        //todo Future succeded => return to ent

                        handler.handle(Future.succeededFuture(dispatchHandler.result().getJsonTicket()));
                    });
                } else {
                    log.error("it failed !" + result.cause().getMessage(), (Object) result.cause().getStackTrace());
                }
            });
        } else {
            handler.handle(Future.failedFuture("unknown source"));
        }
    }

    @Override
    public void triggerTicket(String source, JsonObject data, Handler<AsyncResult<JsonObject>> handler) {
        if (Endpoint.ENDPOINT_GLPI.equals(source)) {
            // traitement du ticket glpi
            glpiEndpoint.trigger(data, result -> {
                if (result.succeeded()) {
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
                    handler.handle(Future.succeededFuture(new JsonObject()));
                } else {
                    handler.handle(Future.failedFuture("ticket creation failed"));
                }
            });
        } else {
            handler.handle(Future.failedFuture("unknown source"));
        }
    }

    private void routeTicket(String source, Ticket ticket, Handler<AsyncResult<JsonObject>> handler) {
        // todo routing
    }

//les fonctions du router service
    //aide defaultdemande serviceImpl

}
