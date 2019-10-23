package fr.openent.supportpivot.model.endpoint;

import fr.openent.supportpivot.constants.JiraConstants;
import fr.openent.supportpivot.constants.PivotConstants;
import fr.openent.supportpivot.deprecatedservices.DefaultDemandeServiceImpl;
import fr.openent.supportpivot.deprecatedservices.DefaultJiraServiceImpl;
import fr.openent.supportpivot.helpers.PivotHttpClient;
import fr.openent.supportpivot.helpers.PivotHttpClientRequest;
import fr.openent.supportpivot.managers.ConfigManager;
import fr.openent.supportpivot.model.ticket.JiraTicket;
import fr.openent.supportpivot.model.ticket.PivotTicket;
import fr.openent.supportpivot.services.HttpClientService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.http.BaseServer;

import java.net.URISyntaxException;
import java.util.Base64;
import java.util.List;


class JiraEndpoint extends BaseServer implements Endpoint {

    private PivotHttpClient httpClient;
    private DefaultDemandeServiceImpl demandeService;
    private DefaultJiraServiceImpl jiraService;
    private static Base64.Encoder encoder = Base64.getMimeEncoder().withoutPadding();


    private static final Logger log = LoggerFactory.getLogger(JiraEndpoint.class);

    JiraEndpoint(HttpClientService httpClientService, DefaultDemandeServiceImpl demandeService, DefaultJiraServiceImpl jiraService) {
        try {
            this.httpClient = httpClientService.getHttpClient(ConfigManager.getInstance().getJiraHost());
        } catch (URISyntaxException e) {
            log.error("invalid uri " + e);
        }

        this.demandeService = demandeService;
        this.jiraService = jiraService;
    }

    @Override
    public void trigger(JsonObject data, Handler<AsyncResult<List<PivotTicket>>> handler) {
    }

    @Override
    public void process(JsonObject ticketData, Handler<AsyncResult<PivotTicket>> handler) {
        final String id_jira = ticketData.getString("idjira");
        this.getJiraTicket(id_jira, result -> {
            HttpClientResponse response = result.result();
            if (response.statusCode() == 200) {
                response.bodyHandler(body -> {
                    JsonObject jsonTicket = new JsonObject(body.toString());
                    jiraService.convertJiraReponseToJsonPivot(jsonTicket, resultPivot -> {
                        log.info(resultPivot);
                        //TODO set PivotTicket -> handle success Pivot ticket.
                    });
                });
            }
        });
    }

    @Override
    public void send(PivotTicket ticket, Handler<AsyncResult<PivotTicket>> handler) {
        //TODO => check if Attributed user is the "support-pivot" one.
        // TODO => check if it's create or update.

        if (ticket.getAttributed().equals(PivotConstants.ATTRIBUTION_CGI) && ticket.getGlpiId() != null) {
            this.getJiraTicket(ticket.getGlpiId(), result -> {
                if (result.succeeded()) {
                    log.info("in case that it works: " + result.result().toString());
                    //TODO in case that we find ticket, map it.
                } else {
                    log.info("in case that it does not works: " + result.cause().getMessage());

                    // IN CASE THAT WE DONT FIND TICKET

                }



                /*if (!result.failed()) {
                 *//*if (result.succeeded()) {
                    this.updateJiraTicket(result.result(), ticket);
                } else {
                    this.createJiraTicket(result.result(), ticket);
                }*//*
                }*/
            });
        }

        /*if (this.token != null) {


        } else {
            log.error("Session error");
        }*/
    }

    private void mapPivotTicketToJira(PivotTicket ticket, Handler<AsyncResult<JiraTicket>> handler) {
        JiraTicket jiraTicket = new JiraTicket();
        ticket.getJsonTicket().forEach(field -> {
            switch (field.getKey()) {
                case PivotConstants.IDGLPI_FIELD:
                    jiraTicket.setGlpiID((String) field.getValue());
                    break;
                case PivotConstants.ID_FIELD:
                    jiraTicket.setEntID((String) field.getValue());
                    break;
                case PivotConstants.TITLE_FIELD:
                    jiraTicket.setTitle((String) field.getValue());
                    break;
                case PivotConstants.DESCRIPTION_FIELD:
                    jiraTicket.setContent((String) field.getValue());
                    break;
            }
        });

        handler.handle(Future.succeededFuture(jiraTicket));
    }

    private void getJiraTicket(String idGlpi, Handler<AsyncResult<HttpClientResponse>> handler) {
        try {
            String uri = ConfigManager.getInstance().getJiraBaseUri() + "search?jql=cf%5B" + JiraConstants.IWS_CUSTOM_FIELD + "%5D~" + idGlpi;
            PivotHttpClientRequest sendingRequest = this.httpClient.createRequest("GET", uri, "");
            this.setHeaderRequest(sendingRequest);

            sendingRequest.startRequest(result -> {
                if (result.succeeded()) {
                    handler.handle(Future.succeededFuture(result.result()));
                } else {
                    handler.handle(Future.failedFuture(result.cause().getMessage()));
                }
            });
        } catch (Exception e) {
            handler.handle(Future.failedFuture(e));
        }
    }

    private void setHeaderRequest(PivotHttpClientRequest request) {
        HttpClientRequest clientRequest = request.getHttpClientRequest();
        clientRequest.putHeader("Authorization", "Basic " + encoder.encodeToString(ConfigManager.getInstance().getJiraAuthInfo().getBytes()))
                .setFollowRedirects(true);
        if (!clientRequest.headers().contains("Content-Type")) {
            clientRequest.putHeader("Content-Type", "application/json");
        }
    }
}
