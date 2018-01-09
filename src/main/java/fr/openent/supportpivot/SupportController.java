package fr.openent.supportpivot;

import fr.openent.supportpivot.service.DemandeService;
import fr.openent.supportpivot.service.impl.DefaultDemandeServiceImpl;
import fr.wseduc.rs.Post;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.email.EmailSender;
import fr.wseduc.webutils.http.Renders;
import fr.wseduc.webutils.request.RequestUtils;
import fr.wseduc.webutils.security.SecuredAction;
import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.email.EmailFactory;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.Container;

import java.util.Map;

import static fr.wseduc.webutils.http.response.DefaultResponseHandler.defaultResponseHandler;

/**
 * Created by colenot on 07/12/2017.
 */
public class SupportController extends ControllerHelper{

    private DemandeService demandeService;

    @Override
    public void init(Vertx vertx, final Container container, RouteMatcher rm,
                     Map<String, SecuredAction> securedActions) {
        super.init(vertx, container, rm, securedActions);
        EmailFactory emailFactory = new EmailFactory(vertx, container, container.config());
        EmailSender emailSender = emailFactory.getSender();
        this.demandeService = new DefaultDemandeServiceImpl(vertx, container, emailSender);
    }

    @Post("/demande")
    public void demandeSupportIWS(final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, new Handler<JsonObject>() {
            @Override
            public void handle(final JsonObject resource) {
                demandeService.addIWS(request, resource, getDefaultResponseHandler(request));
            }
        });
    }


    @Post("/demandeENT")
    public void demandeSupportENT(final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, new Handler<JsonObject>() {
            @Override
            public void handle(final JsonObject resource) {
                demandeService.addENT(request, resource, getDefaultResponseHandler(request));
            }
        });
    }

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


}
