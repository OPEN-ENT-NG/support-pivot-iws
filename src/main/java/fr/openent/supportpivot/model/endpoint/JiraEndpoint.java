package fr.openent.supportpivot.model.endpoint;

import fr.openent.supportpivot.Supportpivot;
import fr.openent.supportpivot.constants.GlpiConstants;
import fr.openent.supportpivot.constants.JiraConstants;
import fr.openent.supportpivot.constants.PivotConstants;
import fr.openent.supportpivot.helpers.LoginTool;
import fr.openent.supportpivot.helpers.ParserTool;
import fr.openent.supportpivot.helpers.PivotHttpClient;
import fr.openent.supportpivot.helpers.PivotHttpClientRequest;
import fr.openent.supportpivot.managers.ConfigManager;
import fr.openent.supportpivot.model.ticket.JiraTicket;
import fr.openent.supportpivot.model.ticket.PivotTicket;
import fr.openent.supportpivot.model.ticket.Ticket;
import fr.openent.supportpivot.services.HttpClientService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.http.BaseServer;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.xpath.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


class JiraEndpoint extends BaseServer implements Endpoint {


    //    private HttpClientService httpClientService;
    private PivotHttpClient httpClient;
    private String token;
    private SimpleDateFormat parser;
    private ConfigManager configManager;

    private static final Logger log = LoggerFactory.getLogger(JiraEndpoint.class);

    JiraEndpoint(HttpClientService httpClientService) {
        this.parser = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        this.configManager = new ConfigManager(config);

        try {
            this.httpClient = httpClientService.getHttpClient(GlpiConstants.HOST_URL);
        } catch (URISyntaxException e) {
            log.error("invalid uri " + e);
        }

        /*LoginTool.getGlpiSessionToken(this.httpClient, handler -> {
            if (handler.succeeded()) {
                this.token = handler.result();
            } else {
                log.error(handler.cause().getMessage(), (Object) handler.cause().getStackTrace());
            }
        });*/
    }

    @Override
    public void trigger(JsonObject data, Handler<AsyncResult<List<PivotTicket>>> handler) {
    }

    @Override
    public void process(JsonObject ticketData, Handler<AsyncResult<PivotTicket>> handler) {
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
        JiraTicket jiraTicket = new JiraTicket(configManager);
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

    private void getJiraTicket(String idGlpi, Handler<AsyncResult<JsonObject>> handler) {
        try {
            PivotHttpClientRequest sendingRequest = this.httpClient
                    .createGetRequest(configManager.getJiraBaseUrl() + "search?jql=cf%5B" + JiraConstants.GLPI_CUSTOM_FIELD + "%5D~" + idGlpi);

            sendingRequest.startRequest(result -> {
                if (result.succeeded()) {
                    result.result().bodyHandler(body -> {
                        log.info("Jira body content" + body.toString());
                        handler.handle(Future.succeededFuture(body.toJsonObject()));
                    });
                } else {
                    handler.handle(Future.failedFuture(result.cause()));
                }
            });
        } catch (Exception e) {
            handler.handle(Future.failedFuture(e));
        }
    }
}
