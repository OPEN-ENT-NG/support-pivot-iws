package fr.openent.supportpivot.managers;

import fr.openent.supportpivot.deprecatedservices.DefaultDemandeServiceImpl;
import fr.openent.supportpivot.deprecatedservices.DefaultJiraServiceImpl;
import fr.openent.supportpivot.deprecatedservices.DemandeService;
import fr.openent.supportpivot.services.*;
import fr.openent.supportpivot.services.routers.CrnaRouterService;
import fr.openent.supportpivot.services.routers.MdpRouterService;
import fr.wseduc.cron.CronTrigger;
import fr.wseduc.webutils.email.EmailSender;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.entcore.common.email.EmailFactory;

import java.text.ParseException;

public class ServiceManager {
    protected static final Logger log = LoggerFactory.getLogger(ServiceManager.class);
    private static ServiceManager serviceManager = null;

    private DefaultDemandeServiceImpl demandeService;
    private DefaultJiraServiceImpl jiraService;
    private GlpiService glpiService;
    private MongoService mongoService;
    private RouterService routeurService;
    private HttpClientService httpClientService;

    public static void init(Vertx vertx, JsonObject config, EventBus eb) {
        if(serviceManager == null) {
            serviceManager = new ServiceManager(vertx, config, eb);
        }
    }

    private ServiceManager(Vertx vertx, JsonObject config, EventBus eb) {

        EmailFactory emailFactory = new EmailFactory(vertx, config);
        EmailSender emailSender = emailFactory.getSender();

       mongoService = new MongoService(ConfigManager.getInstance().getMongoCollection());
       demandeService = new DefaultDemandeServiceImpl(vertx, config, emailSender, mongoService);
       httpClientService = new HttpClientService(vertx);
       jiraService = new DefaultJiraServiceImpl(vertx, config);
        glpiService = new GlpiService(httpClientService);

        switch( ConfigManager.getInstance().getCollectivity() ) {
            case "CRNA":

                log.info("Start Pivot with CRNA Routeur.");
                routeurService = new CrnaRouterService(httpClientService, demandeService, jiraService, glpiService, mongoService, vertx);
                try {
                    ExternalSynchroTask syncLauncherTask = new ExternalSynchroTask(vertx.eventBus());
                    new CronTrigger(vertx, ConfigManager.getInstance().getSynchroCronDate()).schedule(syncLauncherTask);
                } catch (ParseException e) {
                    log.error("Cron Synchro GLPI supprot pivot. synchro-cron : " + ConfigManager.getInstance().getSynchroCronDate(), e);
                }
                break;
            case "MDP":
                log.info("Start Pivot with MDP Routeur.");
                routeurService = new MdpRouterService();
                break;
            default:
                log.error("Unknown value when starting Pivot Service. collectivity: " + ConfigManager.getInstance().getCollectivity());
        } 
    }

    public DemandeService getDemandeService() { return demandeService; }
    public MongoService getMongoService() { return mongoService; }
    public RouterService getRouteurService() { return routeurService; }

    public static ServiceManager getInstance(){ return serviceManager;}



}
