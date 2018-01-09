package fr.openent.supportpivot;

import fr.openent.supportpivot.service.DemandeService;
import fr.openent.supportpivot.service.impl.DefaultDemandeServiceImpl;
import fr.wseduc.bus.BusAddress;
import fr.wseduc.rs.Post;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.http.Renders;
import fr.wseduc.webutils.request.RequestUtils;
import org.entcore.common.controller.ControllerHelper;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonObject;

import static fr.wseduc.webutils.http.response.DefaultResponseHandler.defaultResponseHandler;

/**
 * Created by colenot on 07/12/2017.
 */
public class SupportController extends ControllerHelper{

    private final DemandeService demandeService;

    public SupportController() {
        super();
        this.demandeService = new DefaultDemandeServiceImpl();
    }

    @Post("/demande")
    public void demandeSupport(final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, new Handler<JsonObject>() {
            @Override
            public void handle(final JsonObject resource) {
                demandeService.add(resource, new Handler<Either<String, JsonObject>>() {
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
                });
            }
        });
    }

    @BusAddress("supportpivot.demande")
    public void busEvents(Message<JsonObject> message) {
        JsonObject issue = message.body().getObject("issue");
        message.reply(new JsonObject().putString("status", "ok")
            .putString("message", "invalid.action")
            .putObject("issue", issue));
    }
}
