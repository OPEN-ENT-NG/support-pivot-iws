package fr.openent.supportpivot.model.endpoint;

import fr.openent.supportpivot.constants.GlpiConstants;
import fr.openent.supportpivot.constants.PivotConstants;
import fr.openent.supportpivot.deprecatedservices.DefaultDemandeServiceImpl;
import fr.openent.supportpivot.deprecatedservices.DemandeService;
import fr.openent.supportpivot.helpers.LoginTool;
import fr.openent.supportpivot.helpers.ParserTool;
import fr.openent.supportpivot.helpers.PivotHttpClient;
import fr.openent.supportpivot.helpers.PivotHttpClientRequest;
import fr.openent.supportpivot.managers.ConfigManager;
import fr.openent.supportpivot.managers.ServiceManager;
import fr.openent.supportpivot.model.ticket.PivotTicket;
import fr.openent.supportpivot.model.ticket.Ticket;
import fr.openent.supportpivot.services.HttpClientService;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.xpath.*;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


class PivotEndpoint implements Endpoint {


    //    private HttpClientService httpClientService;
    private PivotHttpClient httpClient;
    private DemandeService demandeService;
    //    private String token;
    private SimpleDateFormat parser;
//    private DefaultDemandeServiceImpl defaultDemandeService;

    private static final Logger log = LoggerFactory.getLogger(PivotEndpoint.class);

    PivotEndpoint(HttpClientService httpClientService, DemandeService demandeService /*DefaultDemandeServiceImpl defaultDemandeService*/) {
//        this.defaultDemandeService = defaultDemandeService;
        this.demandeService = demandeService;
        this.parser = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        try {
            this.httpClient = httpClientService.getHttpClient(GlpiConstants.HOST_URL);
        } catch (URISyntaxException e) {
            log.error("invalid uri " + e);
        }
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
        /*defaultDemandeService.sendToENT(ticket.getJsonTicket(), result -> {
            if(result.isLeft()) {
                log.error("Supportpivot : could not send JIRA ticket to ENT" + result.left().getValue());
            }
        });*/
    }

    private void setHeaderRequest(PivotHttpClientRequest request) {
        request.getHttpClientRequest().putHeader("Content-Type", "text/xml");
    }

    private void getGlpiTickets(Handler<AsyncResult<Document>> handler) {
        try {
            PivotHttpClientRequest sendingRequest = this.httpClient.createPostRequest("toto", "");

            this.setHeaderRequest(sendingRequest);

            sendingRequest.startRequest(result -> {
                if (result.succeeded()) {
                    result.result().bodyHandler(body -> {
                        Document xml = ParserTool.getParsedXml(body);
                        handler.handle(Future.succeededFuture(xml));
                    });
                } else {
                    log.info("fail ");
                    handler.handle(Future.failedFuture(result.cause()));
//                        log.error(result.cause().getMessage());
                }
            });
        } catch (Exception e) {
            log.info("fail 2");
            handler.handle(Future.failedFuture(e));
        }
    }
}
