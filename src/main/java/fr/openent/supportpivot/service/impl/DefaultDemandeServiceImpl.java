package fr.openent.supportpivot.service.impl;

import fr.openent.supportpivot.Supportpivot;
import fr.openent.supportpivot.service.DemandeService;
import fr.openent.supportpivot.service.JiraService;
import fr.wseduc.mongodb.MongoDb;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.email.EmailSender;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static fr.wseduc.webutils.Server.getEventBus;
import static fr.wseduc.webutils.Utils.handlerToAsyncHandler;

/**
 * Created by colenot on 07/12/2017.
 *
 * Default implementation for DemandeService
 */
public class DefaultDemandeServiceImpl implements DemandeService {

    private static final String DEMANDE_COLLECTION = "support.demandes";
    private final MongoDb mongo;
    private final EmailSender emailSender;
    private final Logger log = LoggerFactory.getLogger(DefaultDemandeServiceImpl.class);

    private final EventBus eb;

    private final String MAIL_IWS;
    private final String COLLECTIVITY_NAME;
    private final String ATTRIBUTION_DEFAULT;
    private final String TICKETTYPE_DEFAULT;
    private final String PRIORITY_DEFAULT;

    private final JiraService jiraService;

    private static final String ENT_TRACKERUPDATE_ADDRESS = "support.update.bugtracker";

    public DefaultDemandeServiceImpl(Vertx vertx, JsonObject config, EmailSender emailSender) {
        this.mongo = MongoDb.getInstance();
        this.emailSender = emailSender;

        eb = getEventBus(vertx);
        this.MAIL_IWS = config.getString("mail-iws");
        this.COLLECTIVITY_NAME = config.getString("collectivity");
        this.ATTRIBUTION_DEFAULT = config.getString("default-attribution");
        this.TICKETTYPE_DEFAULT = config.getString("default-tickettype");
        this.PRIORITY_DEFAULT = config.getString("default-priority");
        this.jiraService = new DefaultJiraServiceImpl(vertx, config);
    }

    /**
     * Add issue from IWS
     * Check every mandatory field is present in jsonPivot, then send for treatment
     * @param jsonPivot JSON object in PIVOT format
     */
    public void addIWS(HttpServerRequest request, JsonObject jsonPivot, final Handler<Either<String, JsonObject>> handler) {

        saveTicketToMongo(Supportpivot.ATTRIBUTION_IWS, jsonPivot);

        StringBuilder missingMandatoryFields = new StringBuilder();
        for( String field : Supportpivot.IWS_MANDATORY_FIELDS ) {
            if( ! jsonPivot.containsKey(field) ) {
                missingMandatoryFields.append(field);
                missingMandatoryFields.append(", ");
            }
        }

        if( missingMandatoryFields.length() != 0 ) {
            handler.handle(new Either.Left<>("2;Mandatory Field "+ missingMandatoryFields));
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

        saveTicketToMongo(Supportpivot.ATTRIBUTION_ENT, jsonPivot);

        StringBuilder missingMandatoryFields = new StringBuilder();
        for( String field : Supportpivot.ENT_MANDATORY_FIELDS ) {
            if( ! jsonPivot.containsKey(field) ) {
                missingMandatoryFields.append(field);
                missingMandatoryFields.append(", ");
            }
        }

        if( missingMandatoryFields.length() != 0 ) {
            handler.handle(new Either.Left<>("2;Mandatory Field "+ missingMandatoryFields));

        }
        else {
            if( !jsonPivot.containsKey(Supportpivot.COLLECTIVITY_FIELD) ) {
                jsonPivot.put(Supportpivot.COLLECTIVITY_FIELD, COLLECTIVITY_NAME);
            }
            if( !jsonPivot.containsKey(Supportpivot.ATTRIBUTION_FIELD) ) {
                jsonPivot.put(Supportpivot.ATTRIBUTION_FIELD, ATTRIBUTION_DEFAULT);
            }
            if( !jsonPivot.containsKey(Supportpivot.TICKETTYPE_FIELD) ) {
                jsonPivot.put(Supportpivot.TICKETTYPE_FIELD, TICKETTYPE_DEFAULT);
            }
            if( !jsonPivot.containsKey(Supportpivot.PRIORITY_FIELD) ) {
                jsonPivot.put(Supportpivot.PRIORITY_FIELD, PRIORITY_DEFAULT);
            }
            if( jsonPivot.containsKey(Supportpivot.DATE_CREA_FIELD) ) {
                String sqlDate = jsonPivot.getString(Supportpivot.DATE_CREA_FIELD);
                jsonPivot.put(Supportpivot.DATE_CREA_FIELD, formatSqlDate(sqlDate));
            }
            if( jsonPivot.containsKey(Supportpivot.STATUSENT_FIELD)
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
                jsonPivot.put(Supportpivot.STATUSENT_FIELD, newStatus);

            }

            JsonArray modules = jsonPivot.getJsonArray(Supportpivot.MODULES_FIELD, new fr.wseduc.webutils.collections.JsonArray());
            JsonArray newModules = new fr.wseduc.webutils.collections.JsonArray();
            for(Object o : modules){
                if( o instanceof String ) {
                    String module = (String)o;
                    newModules.add(moduleEntToPivot(module));
                }
            }
            jsonPivot.put(Supportpivot.MODULES_FIELD, newModules);
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
        return Supportpivot.applicationsMap.getOrDefault(moduleName, "Autres");
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
        jsonPivot.put("source", source);
        jsonPivot.put("date", dateFormat.format(date));

        mongo.insert(DEMANDE_COLLECTION, jsonPivot, retourJson -> {
            if (!"ok".equals(retourJson.body().getString("status"))) {
                log.error("Supportpivot : could not save json to mongoDB");
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

        String attribution = jsonPivot.getString(Supportpivot.ATTRIBUTION_FIELD);
        if( !Supportpivot.ATTRIBUTION_CGI.equals(attribution)
                && !Supportpivot.ATTRIBUTION_ENT.equals(attribution)
                && !Supportpivot.ATTRIBUTION_IWS.equals(attribution))
        {
            handler.handle(new Either.Left<>("3;Invalid value for "+Supportpivot.ATTRIBUTION_FIELD ));
            return;
        }

        switch(source) {
            case Supportpivot.ATTRIBUTION_IWS:
                boolean sentToEnt = false;
                if(jsonPivot.containsKey(Supportpivot.IDENT_FIELD)
                        && !jsonPivot.getString(Supportpivot.IDENT_FIELD).isEmpty()) {
                    sendToENT(jsonPivot, handler);
                    sentToEnt = true;
                }
                if(Supportpivot.ATTRIBUTION_CGI.equals(attribution) || (jsonPivot.containsKey(Supportpivot.IDJIRA_FIELD)
                        && !jsonPivot.getString(Supportpivot.IDJIRA_FIELD).isEmpty())) {
                    Handler<Either<String,JsonObject>> newHandler;
                    if(sentToEnt) {
                        newHandler = jiraResponse -> {
                            if(jiraResponse.isLeft()) {
                                log.error("Supportpivot : could not save ticket to JIRA");
                            }
                        };
                    } else  {
                        newHandler = handler;
                    }
                    sendToJIRA(request, jsonPivot, newHandler);
                }
                break;

            case Supportpivot.ATTRIBUTION_ENT:
                sendToIWS(request, jsonPivot, handler);
                if(jsonPivot.containsKey(Supportpivot.IDJIRA_FIELD)
                        && jsonPivot.getString(Supportpivot.IDJIRA_FIELD).isEmpty()) {
                    sendToJIRA(request, jsonPivot, jiraResponse -> {
                        if(jiraResponse.isLeft()) {
                            log.error("Supportpivot : could not save ticket to JIRA");
                        }
                    });
                }
                break;

            case Supportpivot.ATTRIBUTION_CGI:
                sendToIWS(request, jsonPivot, handler);
                if(jsonPivot.containsKey(Supportpivot.IDENT_FIELD)
                        && jsonPivot.getString(Supportpivot.IDENT_FIELD).isEmpty()) {
                    sendToENT(jsonPivot, entResponse -> {
                        if(entResponse.isLeft()) {
                            log.error("Supportpivot : could not send JIRA ticket to ENT" + entResponse.left().getValue());
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

        if(!jsonPivot.containsKey(Supportpivot.IDENT_FIELD)
                || jsonPivot.getString(Supportpivot.IDENT_FIELD).isEmpty()) {
            handler.handle(new Either.Left<>("2;Mandatory field " + Supportpivot.IDENT_FIELD));
            return;
        }
        eb.send(ENT_TRACKERUPDATE_ADDRESS,
                new JsonObject().put("action", "create").put("issue", jsonPivot),
                handlerToAsyncHandler(message -> {
                    if("OK".equals(message.body().getString("status"))) {
                        handler.handle(new Either.Right<>(message.body()));
                    } else {
                        handler.handle(new Either.Left<>("999;" + message.body().toString()));
                    }
                }));
    }


    /**
     * Send pivot information to JIRA
     * @param jsonPivot JSON in pivot format
     */
    private void sendToJIRA(final HttpServerRequest request, JsonObject jsonPivot,
                            final Handler<Either<String, JsonObject>> handler) {
        final boolean ticketExists = (jsonPivot.containsKey(Supportpivot.IDJIRA_FIELD)
                && !jsonPivot.getString(Supportpivot.IDJIRA_FIELD).isEmpty());
        jiraService.sendToJIRA(jsonPivot, stringJsonObjectEither -> {
            if (stringJsonObjectEither.isRight()) {
                if(!ticketExists) {
                    //return to IWS ticket with Id_JIRA
                    sendToIWS(request,
                            stringJsonObjectEither.right().getValue().getJsonObject("jsonPivotCompleted"),
                            handler);
                } else {
                    handler.handle(new Either.Right<>(new JsonObject().put("status", "OK")));
                }
            } else {
                handler.handle(stringJsonObjectEither);
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
            .append("<br/>Collectivite=")
            .append(jsonPivot.getString(Supportpivot.COLLECTIVITY_FIELD))
            .append("<br/>academie=")
            .append(jsonPivot.getString(Supportpivot.ACADEMY_FIELD, ""))
            .append("<br/>demandeur=")
            .append(safeField(jsonPivot.getString(Supportpivot.CREATOR_FIELD)))
            .append("<br/>type_demande=")
            .append(jsonPivot.getString(Supportpivot.TICKETTYPE_FIELD, ""))
            .append("<br/>titre=")
            .append(safeField(jsonPivot.getString(Supportpivot.TITLE_FIELD)))
            .append("<br/>description=")
            .append(safeField(jsonPivot.getString(Supportpivot.DESCRIPTION_FIELD)))
            .append("<br/>priorite=")
            .append(jsonPivot.getString(Supportpivot.PRIORITY_FIELD, ""));

        JsonArray modules =   jsonPivot.getJsonArray(Supportpivot.MODULES_FIELD, new fr.wseduc.webutils.collections.JsonArray());
        mail.append("<br/>modules=[");
        for(int i=0 ; i<modules.size();i++){
            if(i > 0) {
                mail.append(", ");
            }
            mail.append(modules.getString(i));
        }
        mail.append("]");

        mail.append("<br/>id_jira=")
            .append(jsonPivot.getString(Supportpivot.IDJIRA_FIELD, ""))
            .append("<br/>id_ent=")
            .append(jsonPivot.getString(Supportpivot.IDENT_FIELD, ""))
            .append("<br/>id_iws=")
            .append(jsonPivot.getString(Supportpivot.IDIWS_FIELD, ""));

        JsonArray comm = jsonPivot.getJsonArray(Supportpivot.COMM_FIELD, new fr.wseduc.webutils.collections.JsonArray());
        for(int i=0 ; i<comm.size();i++){
            mail.append("<br/>commentaires=")
                    .append(safeField(comm.getString(i)));
        }

        mail.append("<br/>statut_iws=")
            .append(jsonPivot.getString(Supportpivot.STATUSIWS_FIELD, ""))
            .append("<br/>statut_ent=")
            .append(jsonPivot.getString(Supportpivot.STATUSENT_FIELD, ""))
            .append("<br/>statut_jira=")
            .append(jsonPivot.getString(Supportpivot.STATUSJIRA_FIELD, ""))
            .append("<br/>date_creation=")
            .append(jsonPivot.getString(Supportpivot.DATE_CREA_FIELD, ""))
            .append("<br/>date_resolution_iws=")
            .append(jsonPivot.getString(Supportpivot.DATE_RESOIWS_FIELD, ""))
            .append("<br/>date_resolution_ent=")
            .append(jsonPivot.getString(Supportpivot.DATE_RESOENT_FIELD, ""))
            .append("<br/>date_resolution_jira=")
            .append(jsonPivot.getString(Supportpivot.DATE_RESOJIRA_FIELD, ""))
            .append("<br/>reponse_technique=")
            .append(safeField(jsonPivot.getString(Supportpivot.TECHNICAL_RESP_FIELD, "")))
            .append("<br/>reponse_client=")
            .append(safeField(jsonPivot.getString(Supportpivot.CLIENT_RESP_FIELD, "")))
            .append("<br/>attribution=")
            .append(jsonPivot.getString(Supportpivot.ATTRIBUTION_FIELD))
            .append("<br/>");

        String mailTo = jsonPivot.getString("email");
        if( mailTo == null || mailTo.isEmpty() ) {
            mailTo = this.MAIL_IWS;
        }


        //prepare storage in mongo of sent mail to IWS
        JsonObject savedInfo = new JsonObject();
        savedInfo.put("mailContent", mail.toString());
        savedInfo.put(Supportpivot.IDIWS_FIELD, jsonPivot.getString(Supportpivot.IDIWS_FIELD, ""));
        savedInfo.put(Supportpivot.IDENT_FIELD, jsonPivot.getString(Supportpivot.IDENT_FIELD, ""));
        savedInfo.put(Supportpivot.IDJIRA_FIELD, jsonPivot.getString(Supportpivot.IDJIRA_FIELD, ""));
        savedInfo.put("dest", mailTo);

        JsonArray mailAtts = new fr.wseduc.webutils.collections.JsonArray();
        JsonArray savedInfoPJ = new fr.wseduc.webutils.collections.JsonArray();
        JsonArray atts = jsonPivot.getJsonArray(Supportpivot.ATTACHMENT_FIELD, new fr.wseduc.webutils.collections.JsonArray());
        for (Object o : atts) {
            if( !(o instanceof JsonObject)) continue;
            JsonObject jsonAtt = (JsonObject)o;
            if(!jsonAtt.containsKey(Supportpivot.ATTACHMENT_NAME_FIELD)
                    || !jsonAtt.containsKey(Supportpivot.ATTACHMENT_CONTENT_FIELD)) continue;
            JsonObject att = new JsonObject();
            att.put("name", jsonAtt.getString(Supportpivot.ATTACHMENT_NAME_FIELD));
            att.put("content", jsonAtt.getString(Supportpivot.ATTACHMENT_CONTENT_FIELD));
            mailAtts.add(att);

            savedInfoPJ.add(new JsonObject().put("pj_name", jsonAtt.getString(Supportpivot.ATTACHMENT_NAME_FIELD)));
        }
        savedInfo.put("pj", savedInfoPJ);

        //store in mongo sent mail
        saveTicketToMongo("mail", savedInfo);

        if(emailSender==null){
            handler.handle(new Either.Left<>("999;EmailSender module not found"));
            return;
        }

        emailSender.sendEmail(request,
                mailTo,
                null,
                null,
                "TICKETCGI",
                mailAtts,
                mail.toString(),
                null,
                false,
                handlerToAsyncHandler(jsonObjectMessage -> handler.handle(new Either.Right<String, JsonObject>(jsonObjectMessage.body()))));
    }

    /**
     * Send an issue to IWS with fictive info, for testing purpose
     * @param mailTo mail to send to
     */
    @Override
    public void getMongoInfos(HttpServerRequest request, String mailTo, final Handler<Either<String, JsonObject>> handler) {
        try {
            JsonObject req = new JsonObject(java.net.URLDecoder.decode(mailTo, "UTF-8"));
            mongo.find(DEMANDE_COLLECTION, req, jsonObjectMessage -> handler.handle(new Either.Right<String, JsonObject>(jsonObjectMessage.body())));
        } catch(Exception e) {
            handler.handle(new Either.Left<>("Malformed json"));
        }
    }

    /**
     * Send updated informations from a Jira ticket to IWS
     * @param idJira idJira updated in Jira to send to IWS
     */
    @Override
    public void updateJiraToIWS(final HttpServerRequest request,
                                final String idJira,
                                final Handler<Either<String, JsonObject>> handler) {



        jiraService.getTicketUpdatedToIWS(request, idJira, stringJsonObjectEither -> {
            if (stringJsonObjectEither.isRight()) {
                JsonObject jsonPivot = stringJsonObjectEither.right().getValue();
                saveTicketToMongo(Supportpivot.ATTRIBUTION_CGI, jsonPivot);
                add(request,jsonPivot , Supportpivot.ATTRIBUTION_CGI, handler);
            } else {
                handler.handle(new Either.Left<>(
                        "Error, the ticket has not been sent, it doesn't exist."));
            }
        });

    }


}
