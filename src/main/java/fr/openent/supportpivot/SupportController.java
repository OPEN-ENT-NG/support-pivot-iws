package fr.openent.supportpivot;

import fr.openent.supportpivot.service.DemandeService;
import fr.openent.supportpivot.service.impl.DefaultDemandeServiceImpl;
import fr.wseduc.bus.BusAddress;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.email.EmailSender;
import fr.wseduc.webutils.http.Renders;
import fr.wseduc.webutils.request.RequestUtils;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.email.EmailFactory;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.Container;

import java.util.Map;


/**
 * Created by colenot on 07/12/2017.
 *
 * Controller for support pivot
 *
 * Exposed API
 * /demande : Register a demande from IWS
 * /testMail/:mail : Send a test mail to address in parameter
 * /demandeENT : Register a demande from Support module
 */
public class SupportController extends ControllerHelper{

    private DemandeService demandeService;

    @Override
    public void init(Vertx vertx, final Container container, RouteMatcher rm,
                     Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions) {
        super.init(vertx, container, rm, securedActions);
        EmailFactory emailFactory = new EmailFactory(vertx, container, container.config());
        EmailSender emailSender = emailFactory.getSender();
        this.demandeService = new DefaultDemandeServiceImpl(vertx, container, emailSender);
    }

    /**
     * Webservice. Receive info from IWS
     */
    @Post("/demande")
    @SecuredAction("supportpivot.ws.demande")
    public void demandeSupportIWS(final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, new Handler<JsonObject>() {
            @Override
            public void handle(final JsonObject resource) {
                demandeService.addIWS(request, resource, getDefaultResponseHandler(request));
            }
        });
    }

    /**
     * Get a default handler for HttpServerRequest with added info
     * @return handler with error code, error message and status
     */
    private Handler<Either<String, JsonObject>> getDefaultResponseHandler(final HttpServerRequest request){
        return new Handler<Either<String, JsonObject>>() {
            @Override
            public void handle(Either<String, JsonObject> event) {
                if (event.isRight()) {
                    Renders.renderJson(request, event.right().getValue(), 200);
                } else {
                    String errorCode = event.left().getValue();
                    JsonObject error = new JsonObject()
                            .putString("errorCode", errorCode)
                            .putString("errorMessage", "")
                            .putString("status", "KO");
                    Renders.renderJson(request, error, 400);
                }
            }
        };
    }

    /**
     * Internel webservice. Receive info from support module
     */
    @BusAddress("supportpivot.demande")
    public void busEvents(final Message<JsonObject> message) {
        final JsonObject issue = message.body().getObject("issue");
        demandeService.addENT( issue, new Handler<Either<String, JsonObject>>() {
            @Override
            public void handle(Either<String, JsonObject> event) {
                if(event.isRight()) {
                    message.reply(new JsonObject().putString("status", "ok")
                            .putString("message", "invalid.action")
                            .putObject("issue", issue));
                } else {
                    log.error("Supportpivot : error when trying to add ENT issue");
                    message.reply(new JsonObject().putString("status", "ko")
                        .putString("message", event.left().getValue()));
                }
            }
        });

    }

    /**
     * Webservice. Send an issue to specified mail with fictive info, for testing purpose
     */
    @Get("testMail/:mail")
    public void testMailToIWS(final HttpServerRequest request) {
        final String mailTo = request.params().get("mail");
        demandeService.testMailToIWS(request, mailTo, getDefaultResponseHandler(request));
    }

    /**
     * Webservice. Send info updated from Jira to IWS
     */
    @Get("updateJira/:idjira")
    public void udpateJira(final HttpServerRequest request) {
        final String idJira = request.params().get("idjira");
        demandeService.updateJiraToIWS(request, idJira, getDefaultResponseHandler(request));
    }

    /*
    definir quand précisement on met a jour

    griser le champs iws_id_ticket

    URL : http://localhost:8090/supportpivot/updateJira/{{issueid}}
    METHOD : GET
    PARAMETERS : issueid = $issue.id(avoir?)


    */


}
