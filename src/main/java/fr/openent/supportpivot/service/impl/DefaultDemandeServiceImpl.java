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
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

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
            add(request, jsonPivot, Supportpivot.ATTRIBUTION_IWS, handler);
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
            if( jsonPivot.containsField(Supportpivot.STATUSENT_FIELD)
                    && jsonPivot.getString(Supportpivot.STATUSENT_FIELD) != null
                    && !jsonPivot.getString(Supportpivot.STATUSENT_FIELD).isEmpty()) {
                String newStatus;
                switch (jsonPivot.getString(Supportpivot.STATUSENT_FIELD)) {
                    case Supportpivot.STATUSENT_NEW:
                        newStatus = Supportpivot.STATUSPIVOT_NEW;
                        break;
                    case Supportpivot.STATUSENT_OPENED:
                        newStatus = Supportpivot.STATUSPIVOT_OPENED;
                        break;
                    case Supportpivot.STATUSENT_RESOLVED:
                        newStatus = Supportpivot.STATUSPIVOT_RESOLVED;
                        break;
                    case Supportpivot.STATUSENT_CLOSED:
                        newStatus = Supportpivot.STATUSPIVOT_CLOSED;
                        break;
                    default:
                        newStatus = jsonPivot.getString(Supportpivot.STATUSENT_FIELD);
                }
                jsonPivot.putString(Supportpivot.STATUSENT_FIELD, newStatus);

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
            add(null, jsonPivot, Supportpivot.ATTRIBUTION_ENT, handler);
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
        SimpleDateFormat output = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        try {
            Date dateValue = input.parse(sqlDate);
            return output.format(dateValue);
        } catch (ParseException e) {
            log.error("Supportpivot : invalid date format" + e.getMessage());
            return sqlDate;
        }
    }

    private void saveTicketToMongo(final String source, JsonObject jsonPivot) {

        DateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        Date date = new Date();
        jsonPivot.putString("source", source);
        jsonPivot.putString("date", dateFormat.format(date));

        mongo.insert(DEMANDE_COLLECTION, jsonPivot, new Handler<Message<JsonObject>>() {
            @Override
            public void handle(Message<JsonObject> retourJson) {
                if (!"ok".equals(retourJson.body().getString("status"))) {
                    log.error("Supportpivot : could not save json to mongoDB");
                }
            }
        });
    }

    /**
     * Get a JSON in Pivot format, and send the information to the right recipient
     * Save the ticket to mongo before processing
     * @param jsonPivot Json in pivot format
     */
    private void add(final HttpServerRequest request, JsonObject jsonPivot,
                     final String source, final Handler<Either<String, JsonObject>> handler) {

        saveTicketToMongo(source, jsonPivot);

        String attribution = jsonPivot.getString(Supportpivot.ATTRIBUTION_FIELD);
        if( !Supportpivot.ATTRIBUTION_CGI.equals(attribution)
                && !Supportpivot.ATTRIBUTION_ENT.equals(attribution)
                && !Supportpivot.ATTRIBUTION_IWS.equals(attribution))
        {
            handler.handle(new Either.Left<String, JsonObject>("2"));
            return;
        }

        switch(source) {
            case Supportpivot.ATTRIBUTION_IWS:
                switch (attribution) {
                    case Supportpivot.ATTRIBUTION_CGI:
                        sendToJIRA(request, jsonPivot, handler);
                        break;
                    case Supportpivot.ATTRIBUTION_ENT:
                        sendToENT(jsonPivot, handler);
                        break;
                    case Supportpivot.ATTRIBUTION_IWS:
                        boolean sentToEnt = false;
                        if(jsonPivot.containsField(Supportpivot.IDENT_FIELD)
                                && !jsonPivot.getString(Supportpivot.IDENT_FIELD).isEmpty()) {
                            sendToENT(jsonPivot, handler);
                            sentToEnt = true;
                        }
                        if(jsonPivot.containsField(Supportpivot.IDJIRA_FIELD)
                                && jsonPivot.getString(Supportpivot.IDJIRA_FIELD).isEmpty()) {
                            Handler<Either<String,JsonObject>> newHandler;
                            if(sentToEnt) {
                                newHandler = new Handler<Either<String, JsonObject>>() {
                                    @Override
                                    public void handle(Either<String, JsonObject> jiraResponse) {
                                        if(jiraResponse.isLeft()) {
                                            log.error("Supportpivot : could not save ticket to JIRA");
                                        }
                                    }
                                };
                            } else  {
                                newHandler = handler;
                            }
                            sendToJIRA(request, jsonPivot, newHandler);
                        }
                }
                break;

            case Supportpivot.ATTRIBUTION_ENT:
                sendToIWS(request, jsonPivot, handler);
                if(jsonPivot.containsField(Supportpivot.IDJIRA_FIELD)
                        && jsonPivot.getString(Supportpivot.IDJIRA_FIELD).isEmpty()) {
                    sendToJIRA(request, jsonPivot, new Handler<Either<String, JsonObject>>() {
                        @Override
                        public void handle(Either<String, JsonObject> jiraResponse) {
                            if(jiraResponse.isLeft()) {
                                log.error("Supportpivot : could not save ticket to JIRA");
                            }
                        }
                    });
                }
                break;

            case Supportpivot.ATTRIBUTION_CGI:
                sendToIWS(request, jsonPivot, handler);
                if(jsonPivot.containsField(Supportpivot.IDENT_FIELD)
                        && jsonPivot.getString(Supportpivot.IDENT_FIELD).isEmpty()) {
                    sendToENT(jsonPivot, new Handler<Either<String, JsonObject>>() {
                        @Override
                        public void handle(Either<String, JsonObject> entResponse) {
                            if(entResponse.isLeft()) {
                                log.error("Supportpivot : could not send JIRA ticket to ENT");
                            }
                        }
                    });
                }
                break;
        }
    }


    /**
     * Send pivot information to ENT -- using internal bus
     * @param jsonPivot JSON in pivot format
     */
    private void sendToENT(JsonObject jsonPivot, final Handler<Either<String, JsonObject>> handler) {

        if(!jsonPivot.containsField(Supportpivot.IDENT_FIELD)
                || jsonPivot.getString(Supportpivot.IDENT_FIELD).isEmpty()) {
            handler.handle(new Either.Left<String, JsonObject>("2"));
            return;
        }
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
     * Send pivot information to JIRA
     * @param jsonPivot JSON in pivot format
     */
    private void sendToJIRA(final HttpServerRequest request, JsonObject jsonPivot,
                            final Handler<Either<String, JsonObject>> handler) {
        final boolean ticketExists = (jsonPivot.containsField(Supportpivot.IDJIRA_FIELD)
                && !jsonPivot.getString(Supportpivot.IDJIRA_FIELD).isEmpty());
        jiraService.sendToJIRA(jsonPivot, new Handler<Either<String, JsonObject>>() {
            @Override
            public void handle(Either<String, JsonObject> stringJsonObjectEither) {
                if (stringJsonObjectEither.isRight()) {
                    if(!ticketExists) {
                        sendToIWS(request,
                                stringJsonObjectEither.right().getValue().getObject("jsonPivotCompleted"),
                                handler);
                    } else {
                        handler.handle(new Either.Right<String, JsonObject>(new JsonObject().putString("status", "OK")));
                    }
                } else {
                    handler.handle(stringJsonObjectEither);
                }
            }
        });
    }

    private String safeField(String inField) {
        if(inField == null) return null;
        return inField.replace("="," =");
    }

    /**
     * Send pivot information to IWS -- by mail
     * Every data must be htmlEncoded because of encoding / mails incompatibility
     * @param jsonPivot JSON in pivot format
     */
    private void sendToIWS(HttpServerRequest request, JsonObject jsonPivot, final Handler<Either<String, JsonObject>> handler) {

        StringBuilder mail = new StringBuilder()
            .append("collectivite=")
            .append(jsonPivot.getString(Supportpivot.COLLECTIVITY_FIELD))
            .append("\nacademie=")
            .append(jsonPivot.getString(Supportpivot.ACADEMY_FIELD, ""))
            .append("\ndemandeur=")
            .append(safeField(jsonPivot.getString(Supportpivot.CREATOR_FIELD)))
            .append("\ntype_demande=")
            .append(jsonPivot.getString(Supportpivot.TICKETTYPE_FIELD, ""))
            .append("\ntitre=")
            .append(safeField(jsonPivot.getString(Supportpivot.TITLE_FIELD)))
            .append("\ndescription=")
            .append(safeField(jsonPivot.getString(Supportpivot.DESCRIPTION_FIELD)))
            .append("\npriorite=")
            .append(jsonPivot.getString(Supportpivot.PRIORITY_FIELD, ""))
            .append("\nid_jira=")
            .append(jsonPivot.getString(Supportpivot.IDJIRA_FIELD, ""))
            .append("\nid_ent=")
            .append(jsonPivot.getString(Supportpivot.IDENT_FIELD))
            .append("\nid_iws=")
            .append(jsonPivot.getString(Supportpivot.IDIWS_FIELD, ""));

        JsonArray comm = jsonPivot.getArray(Supportpivot.COMM_FIELD, new JsonArray());
        for(int i=0 ; i<comm.size();i++){
            mail.append("\ncommentaires=")
                    .append(safeField((String)comm.get(i)));
        }

        JsonArray modules =   jsonPivot.getArray(Supportpivot.MODULES_FIELD, new JsonArray());
        mail.append("\nmodules=");
        for(int i=0 ; i<modules.size();i++){
            if(i > 0) {
                mail.append(", ");
            }
            mail.append((String)modules.get(i));
        }
        mail.append("\nstatut_iws=")
            .append(jsonPivot.getString(Supportpivot.STATUSIWS_FIELD, ""))
            .append("\nstatut_ent=")
            .append(jsonPivot.getString(Supportpivot.STATUSENT_FIELD, ""))
            .append("\nstatut_jira=")
            .append(jsonPivot.getString(Supportpivot.STATUSJIRA_FIELD, ""))
            .append("\ndate_creation=")
            .append(jsonPivot.getString(Supportpivot.DATE_CREA_FIELD, ""))
            .append("\ndate_resolution_iws=")
            .append(jsonPivot.getString(Supportpivot.DATE_RESOIWS_FIELD, ""))
            .append("\ndate_resolution_ent=")
            .append(jsonPivot.getString(Supportpivot.DATE_RESOENT_FIELD, ""))
            .append("\ndate_resolution_jira=")
            .append(jsonPivot.getString(Supportpivot.DATE_RESOJIRA_FIELD, ""))
            .append("\nreponse_technique=")
            .append(safeField(jsonPivot.getString(Supportpivot.TECHNICAL_RESP_FIELD, "")))
            .append("\nreponse_client=")
            .append(safeField(jsonPivot.getString(Supportpivot.CLIENT_RESP_FIELD, "")))
            .append("\nattribution=")
            .append(jsonPivot.getString(Supportpivot.ATTRIBUTION_FIELD));

        String mailTo = jsonPivot.getString("email");
        if( mailTo == null || mailTo.isEmpty() ) {
            mailTo = this.MAIL_IWS;
        }

        JsonObject savedInfo = new JsonObject();
        savedInfo.putString("mailContent", mail.toString());
        savedInfo.putString(Supportpivot.IDIWS_FIELD, jsonPivot.getString(Supportpivot.IDIWS_FIELD, ""));
        savedInfo.putString(Supportpivot.IDENT_FIELD, jsonPivot.getString(Supportpivot.IDENT_FIELD, ""));
        savedInfo.putString(Supportpivot.IDJIRA_FIELD, jsonPivot.getString(Supportpivot.IDJIRA_FIELD, ""));

        saveTicketToMongo("mail", savedInfo);

        JsonArray mailAtts = new JsonArray();
        JsonArray atts = jsonPivot.getArray(Supportpivot.ATTACHMENT_FIELD, new JsonArray());
        for (Object o : atts) {
            if( !(o instanceof JsonObject)) continue;
            JsonObject jsonAtt = (JsonObject)o;
            if(!jsonAtt.containsField(Supportpivot.ATTACHMENT_NAME_FIELD)
                    || !jsonAtt.containsField(Supportpivot.ATTACHMENT_CONTENT_FIELD)) continue;
            JsonObject att = new JsonObject();
            att.putString("name", jsonAtt.getString(Supportpivot.ATTACHMENT_NAME_FIELD));
            att.putString("content", jsonAtt.getString(Supportpivot.ATTACHMENT_CONTENT_FIELD));
            mailAtts.addObject(att);
        }

        emailSender.sendEmail(request,
                mailTo,
                null,
                null,
                "TICKETCGI",
                //mailAtts,
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
                .putArray(Supportpivot.ATTACHMENT_FIELD, new JsonArray()
                    .add(new JsonObject()
                                .putString(Supportpivot.ATTACHMENT_NAME_FIELD, "toto.txt")
                                .putString(Supportpivot.ATTACHMENT_CONTENT_FIELD, "dHVidWRpLCB0dWJ1ZGE=")))
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
                    add(request, stringJsonObjectEither.right().getValue(), Supportpivot.ATTRIBUTION_CGI, handler);
                } else {
                    handler.handle(new Either.Left<String, JsonObject>(
                            "Error, the ticket has not been sent, it doesn't exist."));
                }
            }
        });

    }


}
