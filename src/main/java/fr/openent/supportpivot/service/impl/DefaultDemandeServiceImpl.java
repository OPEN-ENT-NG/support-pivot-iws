package fr.openent.supportpivot.service.impl;

import fr.openent.supportpivot.Supportpivot;
import fr.openent.supportpivot.service.DemandeService;
import fr.openent.supportpivot.service.JiraService;
import fr.wseduc.mongodb.MongoDb;
import fr.wseduc.webutils.Either;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import fr.wseduc.webutils.email.EmailSender;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.platform.Container;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.vertx.java.core.http.HttpServerRequest;

import static fr.wseduc.webutils.Server.getEventBus;

/**
 * Created by colenot on 07/12/2017.
 *
 * Default implementation for DemandeService
 */
public class DefaultDemandeServiceImpl implements DemandeService {

    private static final String DEMANDE_COLLECTION = "support.demandes";
    private final MongoDb mongo;
    private final EmailSender emailSender;
    private final Logger log;

    private final EventBus eb;

    private final String MAIL_IWS;
    private final String COLLECTIVITY_DEFAULT;
    private final String ATTRIBUTION_DEFAULT;
    private final String TICKETTYPE_DEFAULT;
    private final String PRIORITY_DEFAULT;

    private final JiraService jiraService;

    private static final String ENT_TRACKERUPDATE_ADDRESS = "support.update.bugtracker";

    public DefaultDemandeServiceImpl(Vertx vertx, Container container, EmailSender emailSender) {
        this.mongo = MongoDb.getInstance();
        this.emailSender = emailSender;
        this.log = container.logger();
        eb = getEventBus(vertx);
        this.MAIL_IWS = container.config().getString("mail-iws");
        this.COLLECTIVITY_DEFAULT = container.config().getString("default-collectivity");
        this.ATTRIBUTION_DEFAULT = container.config().getString("default-attribution");
        this.TICKETTYPE_DEFAULT = container.config().getString("default-tickettype");
        this.PRIORITY_DEFAULT = container.config().getString("default-priority");
        this.jiraService = new DefaultJiraServiceImpl(vertx, container, emailSender);
    }

    /**
     * Add issue from IWS
     * Check every mandatory field is present in jsonPivot, then send for treatment
     * @param jsonPivot JSON object in PIVOT format
     */
    public void addIWS(HttpServerRequest request, JsonObject jsonPivot, final Handler<Either<String, JsonObject>> handler) {

        Boolean isAllMandatoryFieldsPresent = true;
        for( String field : Supportpivot.IWS_MANDATORY_FIELDS ) {
            if( ! jsonPivot.containsField(field) ) {
                isAllMandatoryFieldsPresent = false;
            }
        }
        if( ! isAllMandatoryFieldsPresent ) {
            handler.handle(new Either.Left<String, JsonObject>("2"));
        }
        else {
            add(request, jsonPivot, handler);
        }

    }

    /**
     * Add issue from ENT
     * - Check every mandatory field is present in jsonPivot, then send for treatment
     * - Replace empty values by default ones
     * - Replace modules names
     * @param jsonPivot JSON object in PIVOT format
     */
    public void addENT(JsonObject jsonPivot, final Handler<Either<String, JsonObject>> handler) {

        Boolean isAllMandatoryFieldsPresent = true;
        for( String field : Supportpivot.ENT_MANDATORY_FIELDS ) {
            if( ! jsonPivot.containsField(field) ) {
                isAllMandatoryFieldsPresent = false;
            }
        }
        if( ! isAllMandatoryFieldsPresent ) {
            handler.handle(new Either.Left<String, JsonObject>("2"));
        }
        else {
            if( !jsonPivot.containsField(Supportpivot.COLLECTIVITY_FIELD) ) {
                jsonPivot.putString(Supportpivot.COLLECTIVITY_FIELD, COLLECTIVITY_DEFAULT);
            }
            if( !jsonPivot.containsField(Supportpivot.ATTRIBUTION_FIELD) ) {
                jsonPivot.putString(Supportpivot.ATTRIBUTION_FIELD, ATTRIBUTION_DEFAULT);
            }
            if( !jsonPivot.containsField(Supportpivot.TICKETTYPE_FIELD) ) {
                jsonPivot.putString(Supportpivot.TICKETTYPE_FIELD, TICKETTYPE_DEFAULT);
            }
            if( !jsonPivot.containsField(Supportpivot.PRIORITY_FIELD) ) {
                jsonPivot.putString(Supportpivot.PRIORITY_FIELD, PRIORITY_DEFAULT);
            }
            if( jsonPivot.containsField(Supportpivot.DATE_CREA_FIELD) ) {
                String sqlDate = jsonPivot.getString(Supportpivot.DATE_CREA_FIELD);
                jsonPivot.putString(Supportpivot.DATE_CREA_FIELD, formatSqlDate(sqlDate));
            }

            JsonArray modules = jsonPivot.getArray(Supportpivot.MODULES_FIELD, new JsonArray());
            JsonArray newModules = new JsonArray();
            for(Object o : modules){
                if( o instanceof String ) {
                    String module = (String)o;
                    newModules.addString(moduleEntToPivot(module));
                }
            }
            jsonPivot.putArray(Supportpivot.MODULES_FIELD, newModules);
            add(null, jsonPivot, handler);
        }
    }

    /**
     * Get a modules name ENT-like, returns the module name PIVOT-like
     * Returns original name by default
     * @param moduleName ENT-like module name
     * @return PIVOT-like module name encoded in UTF-8
     */
    private String moduleEntToPivot(String moduleName) {
        if (Supportpivot.applicationsMap.containsKey(moduleName)) {
            return Supportpivot.applicationsMap.get(moduleName);
        } else {
            return "Autres";
        }
    }

    /**
     * Format a Date from SQL-like format to dd/MM/yyyy
     * @param sqlDate date to format
     * @return The date formatted, or the original date is case of error
     */
    private String formatSqlDate(String sqlDate) {
        SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
        SimpleDateFormat output = new SimpleDateFormat("dd/MM/yyyy");
        try {
            Date dateValue = input.parse(sqlDate);
            return output.format(dateValue);
        } catch (ParseException e) {
            log.error("Supportpivot : invalid date format" + e.getMessage());
            return sqlDate;
        }
    }

    /**
     * Get a JSON in Pivot format, and send the information to the right recipient
     * If no or unknown recipient, save it to mongo
     * @param jsonPivot Json in pivot format
     */
    private void add(HttpServerRequest request, JsonObject jsonPivot, final Handler<Either<String, JsonObject>> handler) {

        switch(jsonPivot.getString(Supportpivot.ATTRIBUTION_FIELD)) {
            case Supportpivot.ATTRIBUTION_IWS:
                sendToIWS(request, jsonPivot, handler);
                break;
            case Supportpivot.ATTRIBUTION_ENT:
                sendToENT(jsonPivot, handler);
                break;
            case Supportpivot.ATTRIBUTION_CGI:
                jiraService.sendToJIRA(jsonPivot, handler);
                break;
            default:
                mongo.insert(DEMANDE_COLLECTION, jsonPivot, new Handler<Message<JsonObject>>() {
                    @Override
                    public void handle(Message<JsonObject> retourJson) {
                        JsonObject body = retourJson.body();
                        if ("ok".equals(body.getString("status"))) {
                            handler.handle(new Either.Right<String, JsonObject>(new JsonObject().putString("status", "OK")));
                        } else {
                            handler.handle(new Either.Left<String, JsonObject>("1"));
                        }
                    }
                });
        }
    }


    /**
     * Send pivot information to ENT -- using internal bus
     * @param jsonPivot JSON in pivot format
     */
    private void sendToENT(JsonObject jsonPivot, final Handler<Either<String, JsonObject>> handler) {
        eb.send(ENT_TRACKERUPDATE_ADDRESS,
                new JsonObject().putString("action", "create").putObject("issue", jsonPivot),
                new Handler<Message<JsonObject>>() {
                    @Override
                    public void handle(Message<JsonObject> message) {
                        if("OK".equals(message.body().getString("status"))) {
                            handler.handle(new Either.Right<String, JsonObject>(message.body()));
                        } else {
                            handler.handle(new Either.Left<String, JsonObject>(message.body().toString()));
                        }
                    }
                });
    }


    /**
     * Send pivot information to IWS -- by mail
     * Every data must be htmlEncoded because of encoding / mails incompatibility
     * @param jsonPivot JSON in pivot format
     */
    private void sendToIWS(HttpServerRequest request, JsonObject jsonPivot, final Handler<Either<String, JsonObject>> handler) {

        StringBuilder mail = new StringBuilder()
            .append("collectivite = ")
            .append(jsonPivot.getString(Supportpivot.COLLECTIVITY_FIELD))
            .append("\nacademie = ")
            .append(jsonPivot.getString(Supportpivot.ACADEMY_FIELD, ""))
            .append("\ndemandeur = ")
            .append(jsonPivot.getString(Supportpivot.CREATOR_FIELD))
            .append("\ntype_demande = ")
            .append(jsonPivot.getString(Supportpivot.TICKETTYPE_FIELD, ""))
            .append("\ntitre = ")
            .append(jsonPivot.getString(Supportpivot.TITLE_FIELD))
            .append("\ndescription = ")
            .append(jsonPivot.getString(Supportpivot.DESCRIPTION_FIELD))
            .append("\npriorite = ")
            .append(jsonPivot.getString(Supportpivot.PRIORITY_FIELD, ""))
            .append("\nid_jira = ")
            .append(jsonPivot.getString(Supportpivot.IDJIRA_FIELD, ""))
            .append("\nid_ent = ")
            .append(jsonPivot.getString(Supportpivot.IDENT_FIELD))
            .append("\nid_iws = ")
            .append(jsonPivot.getString(Supportpivot.IDIWS_FIELD, ""));

        JsonArray comm = jsonPivot.getArray(Supportpivot.COMM_FIELD, new JsonArray());
        for(int i=0 ; i<comm.size();i++){
            mail.append("\ncommentaires = ")
                    .append((String)comm.get(i));
        }

        JsonArray modules =   jsonPivot.getArray(Supportpivot.MODULES_FIELD, new JsonArray());
        mail.append("\nmodules = ");
        for(int i=0 ; i<modules.size();i++){
            if(i > 0) {
                mail.append(", ");
            }
            mail.append((String)modules.get(i));
        }
        mail.append("\nstatut_iws = ")
            .append(jsonPivot.getString(Supportpivot.STATUSIWS_FIELD, ""))
            .append("\nstatut_ent = ")
            .append(jsonPivot.getString(Supportpivot.STATUSENT_FIELD, ""))
            .append("\nstatut_jira = ")
            .append(jsonPivot.getString(Supportpivot.STATUSJIRA_FIELD, ""))
            .append("\ndate_creation = ")
            .append(jsonPivot.getString(Supportpivot.DATE_CREA_FIELD, ""))
            .append("\ndate_resolution_iws = ")
            .append(jsonPivot.getString(Supportpivot.DATE_RESOIWS_FIELD, ""))
            .append("\ndate_resolution_ent = ")
            .append(jsonPivot.getString(Supportpivot.DATE_RESOENT_FIELD, ""))
            .append("\ndate_resolution_jira = ")
            .append(jsonPivot.getString(Supportpivot.DATE_RESOJIRA_FIELD, ""))
            .append("\nreponse_technique = ")
            .append(jsonPivot.getString(Supportpivot.TECHNICAL_RESP_FIELD, ""))
            .append("\nreponse_client = ")
            .append(jsonPivot.getString(Supportpivot.CLIENT_RESP_FIELD, ""))
            .append("\nattribution = ")
            .append(jsonPivot.getString(Supportpivot.ATTRIBUTION_FIELD));

        String mailTo = jsonPivot.getString("email");
        if( mailTo == null || mailTo.isEmpty() ) {
            mailTo = this.MAIL_IWS;
        }

        emailSender.sendEmail(request,
                mailTo,
                null,
                null,
                "TICKETCGI",
                mail.toString(),
                null,
                false,
                new Handler<Message<JsonObject>>() {
                    @Override
                    public void handle(Message<JsonObject> jsonObjectMessage) {
                        handler.handle(new Either.Right<String, JsonObject>(jsonObjectMessage.body()));
                    }
                });
    }

    /**
     * Send an issue to IWS with fictive info, for testing purpose
     * @param mailTo mail to send to
     */
    @Override
    public void testMailToIWS(HttpServerRequest request, String mailTo, Handler<Either<String, JsonObject>> handler) {
        JsonObject resource = new JsonObject()
                .putString(Supportpivot.COLLECTIVITY_FIELD, "MDP")
                .putString(Supportpivot.ACADEMY_FIELD, "Paris")
                .putString(Supportpivot.CREATOR_FIELD, "Jean Dupont")
                .putString(Supportpivot.TICKETTYPE_FIELD, "Assistance")
                .putString(Supportpivot.TITLE_FIELD, stringEncode("Demande générique de test"))
                .putString(Supportpivot.DESCRIPTION_FIELD, stringEncode("Demande afin de tester la création de demande vers IWS"))
                .putString(Supportpivot.PRIORITY_FIELD, "Mineure")
                .putArray(Supportpivot.MODULES_FIELD,
                        new JsonArray().addString(stringEncode("Actualités")).addString("Assistance ENT"))
                .putString(Supportpivot.IDENT_FIELD, "42")
                .putArray(Supportpivot.COMM_FIELD, new JsonArray()
                    .addString("Jean Dupont| 17/11/2071 | La correction n'est pas urgente.")
                    .addString(stringEncode("Administrateur Etab | 10/01/2017 | La demande a été transmise")))
                .putString(Supportpivot.STATUSENT_FIELD, "Nouveau")
                .putString(Supportpivot.DATE_CREA_FIELD, "16/11/2017")
                .putString(Supportpivot.ATTRIBUTION_FIELD, "IWS")
                .putString("email", mailTo);

        sendToIWS(request, resource, handler);
    }

    /**
     * Encode a string in UTF-8
     * @param in String to encode
     * @return encoded String
     */
    private String stringEncode(String in) {
        return new String(in.getBytes(), StandardCharsets.UTF_8);
    }


    /**
     * Send updated informations from a Jira ticket to IWS
     * @param idJira idJira updated in Jira to send to IWS
     */
    @Override
    public void updateJiraToIWS(final HttpServerRequest request,
                                final String idJira,
                                final Handler<Either<String, JsonObject>> handler) {

        jiraService.getTicketUpdatedToIWS(request, idJira, new Handler<Either<String, JsonObject>>() {
            @Override
            public void handle(Either<String, JsonObject> stringJsonObjectEither) {
                if (stringJsonObjectEither.isRight()) {
                    sendToIWS(request, stringJsonObjectEither.right().getValue(), handler);
                } else {
                    handler.handle(new Either.Left<String, JsonObject>(
                            "Error, the ticket has not been sent, it doesn't exist."));
                }
            }
        });

    }


}
