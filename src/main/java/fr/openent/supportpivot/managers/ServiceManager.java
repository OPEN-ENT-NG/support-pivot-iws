package fr.openent.supportpivot.managers;

import fr.openent.supportpivot.Supportpivot;
import fr.openent.supportpivot.deprecatedservices.DefaultDemandeServiceImpl;
import fr.openent.supportpivot.deprecatedservices.DemandeService;
import fr.openent.supportpivot.services.MongoService;
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

    public static ServiceManager init(Vertx vertx, JsonObject config, EventBus eb) {
        if(serviceManager == null) {
            serviceManager = new ServiceManager(vertx, config, eb);
        }
        return serviceManager;
    }

    @SuppressWarnings("unused")
    private ServiceManager(Vertx vertx, JsonObject config, EventBus eb) {

        ConfigManager appConfig = Supportpivot.appConfig;

        EmailFactory emailFactory = new EmailFactory(vertx, config);
        EmailSender emailSender = emailFactory.getSender();

        this.mongoService = new MongoService(appConfig.getMongoCollection());
        this.demandeService = new DefaultDemandeServiceImpl(vertx, config, emailSender, mongoService);

    }

    public DemandeService getDemandeService() { return demandeService; }
    public MongoService getMongoService() { return mongoService; }
}
