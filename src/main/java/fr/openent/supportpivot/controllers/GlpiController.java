package fr.openent.supportpivot.controllers;

import fr.openent.supportpivot.Supportpivot;
import fr.openent.supportpivot.constants.PivotConstants;
import fr.openent.supportpivot.deprecatedservices.DefaultGLPIService;
import fr.openent.supportpivot.deprecatedservices.DefaultJiraServiceImpl;
import fr.openent.supportpivot.deprecatedservices.JiraService;
import fr.openent.supportpivot.helpers.PivotHttpClient;
import fr.openent.supportpivot.helpers.PivotHttpClientRequest;
import fr.openent.supportpivot.managers.ServiceManager;
import fr.openent.supportpivot.model.endpoint.Endpoint;
import fr.openent.supportpivot.services.HttpClientService;
import fr.openent.supportpivot.services.RouterService;
import fr.wseduc.bus.BusAddress;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.webutils.http.Renders;
import fr.wseduc.webutils.security.SecuredAction;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.controller.ControllerHelper;
import org.vertx.java.core.http.RouteMatcher;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Created by bertinettia on 12/08/2019.
 * <p>
 * Controller for glpi
 */

public class GlpiController extends ControllerHelper {
    private RouterService routerService;

    protected static final Logger log = LoggerFactory.getLogger(GlpiController.class);

    @Override
    public void init(Vertx vertx, final JsonObject config, RouteMatcher rm,
                     Map<String, SecuredAction> securedActions) {

        super.init(vertx, config, rm, securedActions);
        ServiceManager serviceManager = ServiceManager.init(vertx, config, eb);
        this.routerService = serviceManager.getRouteurService();

    }

    @Get("/glpi/test/trigger")
    @fr.wseduc.security.SecuredAction("glpi.test.trigger")
    public void testTrigger(final HttpServerRequest request) {
        routerService.triggerTicket(Endpoint.ENDPOINT_GLPI, new JsonObject(), event -> {
            if (event.succeeded()) {
                Renders.renderJson(request, new JsonObject().put("status", "OK"), 200);
            } else {
                Renders.renderJson(request, new JsonObject().put("status", "KO"), 500);
            }
        });
    }

    @BusAddress("supportpivot.glpi.trigger")
    public void glpiTrigger(Message<JsonObject> message) {}
}
