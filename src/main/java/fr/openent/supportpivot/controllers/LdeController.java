package fr.openent.supportpivot.controllers;

import fr.openent.supportpivot.constants.PivotConstants;
import fr.openent.supportpivot.managers.ServiceManager;
import fr.openent.supportpivot.services.RouterService;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Put;
import fr.wseduc.security.ActionType;
import fr.wseduc.webutils.http.Renders;
import fr.wseduc.webutils.request.RequestUtils;
import fr.wseduc.webutils.security.SecuredAction;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.controller.ControllerHelper;
import org.vertx.java.core.http.RouteMatcher;

import java.util.Map;

/**
 * Created by bertinettia on 12/08/2019.
 * <p>
 * Controller for glpi
 */

public class LdeController extends ControllerHelper {
    private static String SOURCE = PivotConstants.SOURCES.LDE.toString();
    private RouterService routerService;

    protected static final Logger log = LoggerFactory.getLogger(LdeController.class);

    @Override
    public void init(Vertx vertx, final JsonObject config, RouteMatcher rm,
                     Map<String, SecuredAction> securedActions) {

        super.init(vertx, config, rm, securedActions);
        ServiceManager serviceManager = ServiceManager.getInstance();
        this.routerService = serviceManager.getRouteurService();
    }

    @Get("/lde/tickets")
    @fr.wseduc.security.SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void getListeTicketsLDE(final HttpServerRequest request) {
        routerService.readTickets(SOURCE, null, event -> {
            if (event.succeeded()){
                Renders.renderJson(request, event.result());
            }else{
                log.error("GET /lde/tickets failed : ",event.cause());
                Renders.badRequest(request,event.cause().toString());
            }
        });
    }

    @Get("/lde/ticket/:id")
    @fr.wseduc.security.SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void getTicketLDE(final HttpServerRequest request) {
        String id_param_value = request.params().get("id");
        //router trigger ( src = lde + idLDE )
        JsonObject data = new JsonObject().put("idjira", id_param_value);
        routerService.readTickets(SOURCE,data, event -> {
            if (event.succeeded()){
                Renders.renderJson(request, event.result().getJsonObject(0));
            }else{
                log.error("GET /lde/ticket/"+ id_param_value +" failed : ",event.cause());
                Renders.badRequest(request,event.cause().toString());
            }
        });
    }

    @Put("/lde/ticket")
    @fr.wseduc.security.SecuredAction(value = "", type = ActionType.AUTHENTICATED)
    public void putTicketLDE(final HttpServerRequest request) {
        RequestUtils.bodyToJson(request, body -> {
            routerService.processTicket(SOURCE,body, event -> {
                if (event.succeeded()){
                    Renders.renderJson(request, event.result());
                }else{
                    log.error("PUT /lde/ticket/ failed : ",event.cause());
                    Renders.badRequest(request,event.cause().toString());
                }
            });
        });
    }

}
