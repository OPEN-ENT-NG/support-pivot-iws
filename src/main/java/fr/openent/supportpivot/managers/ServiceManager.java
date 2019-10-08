package fr.openent.supportpivot.managers;

import fr.openent.supportpivot.Supportpivot;
import fr.openent.supportpivot.deprecatedservices.DefaultDemandeServiceImpl;
import fr.openent.supportpivot.deprecatedservices.DemandeService;
import fr.openent.supportpivot.helpers.LoginTool;
import fr.openent.supportpivot.helpers.ParserTool;
import fr.openent.supportpivot.services.*;
import fr.openent.supportpivot.services.routers.CrnaRouterService;
import fr.wseduc.webutils.email.EmailSender;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import org.entcore.common.email.EmailFactory;

public class ServiceManager {
    private static ServiceManager serviceManager = null;


    // Deprecated Services
    private DemandeService demandeService;
    private MongoService mongoService;
    private RouterService routeurService;
    private HttpClientService httpClientService;
    private LoginTool loginService;
    private ParserTool parserService;

    public static ServiceManager init(Vertx vertx, JsonObject config, EventBus eb) {
        if(serviceManager == null) {
            serviceManager = new ServiceManager(vertx, config, eb);
        }
        return serviceManager;
    }

    @SuppressWarnings("unused")
    private ServiceManager(Vertx vertx, JsonObject config, EventBus eb) {

        EmailFactory emailFactory = new EmailFactory(vertx, config);
        EmailSender emailSender = emailFactory.getSender();

        this.mongoService = new MongoService(ConfigManager.getInstance().getMongoCollection());
        this.demandeService = new DefaultDemandeServiceImpl(vertx, config, emailSender, mongoService);
        this.httpClientService = new HttpClientService(vertx);
        this.routeurService = new CrnaRouterService(httpClientService, demandeService, vertx);
    }

    public DemandeService getDemandeService() { return demandeService; }
    public MongoService getMongoService() { return mongoService; }
    public RouterService getRouteurService() { return routeurService; }
    public HttpClientService getHttpClientService() { return httpClientService; }
    /*public LoginTool getLoginService() { return loginService; }
    public ParserService getParserService() { return parserService; }*/
}
