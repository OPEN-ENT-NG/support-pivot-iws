package fr.openent.supportpivot.model.endpoint;

import fr.openent.supportpivot.deprecatedservices.DefaultDemandeServiceImpl;
import fr.openent.supportpivot.deprecatedservices.DefaultJiraServiceImpl;
import fr.openent.supportpivot.helpers.PivotHttpClient;
import fr.openent.supportpivot.helpers.PivotHttpClientRequest;
import fr.openent.supportpivot.managers.ConfigManager;
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

import java.net.URI;
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
        this.getJiraTicketByJiraId(id_jira, result -> {
            if(result.failed()){
                handler.handle(Future.failedFuture(result.cause()));
            }else {
                HttpClientResponse response = result.result();
                if (response.statusCode() == 200) {
                    response.bodyHandler(body -> {
                        JsonObject jsonTicket = new JsonObject(body.toString());
                        jiraService.convertJiraReponseToJsonPivot(jsonTicket, resultPivot -> {
                            if (resultPivot.isRight()) {
                                PivotTicket pivotTicket = new PivotTicket();
                                pivotTicket.setJsonObject(resultPivot.right().getValue());
                                handler.handle(Future.succeededFuture(pivotTicket));
                            }else{
                                handler.handle(Future.failedFuture("process jira ticket failed " + resultPivot.left().getValue()));
                            }
                        });
                    });
                }else{
                    response.bodyHandler(body -> log.error(response.statusCode() + " " + response.statusMessage() + "  " + body));
                    handler.handle(Future.failedFuture("process jira ticket failed"));
                }
            }
        });
    }

    @Override
    public void send(PivotTicket ticket, Handler<AsyncResult<PivotTicket>> handler) {
        if (ticket.getExternalId() != null && ticket.getAttributed() != null /*&& ticket.getAttributed().equals(PivotConstants.ATTRIBUTION_NAME)*/) {
            this.getJiraTicketByExternalId(ticket.getExternalId(), result -> {
                if (result.succeeded()) {
                    HttpClientResponse response = result.result();
                    if (response.statusCode() == 200) {
                        response.bodyHandler(body -> {
                            JsonObject jsonTicket = new JsonObject(body.toString());
                            if (jsonTicket.getInteger("total") >= 1) {
                                ticket.setJiraId(jsonTicket.getJsonArray("issues").getJsonObject(0).getString("id"));
                            }
                            this.demandeService.sendToJIRA(ticket.getJsonTicket(), resultJira -> {
                                if (resultJira.succeeded()) {
                                    PivotTicket pivotTicket = new PivotTicket();
                                    pivotTicket.setJsonObject(resultJira.result());
                                    handler.handle(Future.succeededFuture(pivotTicket));
                                } else {
                                    handler.handle(Future.failedFuture(resultJira.cause().getMessage()));
                                }
                            });
                        });
                    } else {
                        log.error( response.request().absoluteURI()+ " : " +response.statusCode() + " " + response.statusMessage());
                        response.bodyHandler(buffer-> {
                                log.error(buffer.getString(0, buffer.length()));
                        });
                        handler.handle(Future.failedFuture("A problem occurred when trying to get ticket from jira (id_glpi: " + ticket.getExternalId() + ")"));
                    }

                } else {
                    log.error("error when getJiraTicket : " , result.cause());
                    handler.handle(Future.failedFuture("A problem occurred when trying to get ticket from jira (id_glpi: " + ticket.getExternalId() ));
                }
            });
        } else {
            handler.handle(Future.failedFuture("Ticket (id_glpi: " + ticket.getExternalId() + ") is not attributed"));
        }
    }
/*
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
*/
    private void getJiraTicketByJiraId(String idJira, Handler<AsyncResult<HttpClientResponse>> handler) {
        URI uri = ConfigManager.getInstance().getJiraBaseUrl().resolve("issue/"+ idJira);
        getJiraTicket(uri, handler);
    }

    private void getJiraTicketByExternalId(String idExternal, Handler<AsyncResult<HttpClientResponse>> handler) {
        String idCustomField = ConfigManager.getInstance().getJiraCustomFieldIdForExternalId().replaceAll("customfield_", "");
        URI uri = ConfigManager.getInstance().getJiraBaseUrl().resolve("search?jql=cf%5B" + idCustomField + "%5D~" + idExternal);
        getJiraTicket(uri, handler);
    }

        private void getJiraTicket(URI uri, Handler<AsyncResult<HttpClientResponse>> handler) {
        try {


            PivotHttpClientRequest sendingRequest = this.httpClient.createRequest("GET", uri.toString(), "");
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
