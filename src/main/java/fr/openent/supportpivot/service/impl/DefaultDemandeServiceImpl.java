package fr.openent.supportpivot.service.impl;

import fr.openent.supportpivot.Supportpivot;
import fr.openent.supportpivot.service.DemandeService;
import fr.wseduc.mongodb.MongoDb;
import fr.wseduc.webutils.Either;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.*;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import fr.wseduc.webutils.email.EmailSender;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.platform.Container;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.impl.Base64;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private final Vertx vertx;

    private final EventBus eb;

    private final String MAIL_IWS;
    private final String COLLECTIVITY_DEFAULT;
    private final String ATTRIBUTION_DEFAULT;
    private final String TICKETTYPE_DEFAULT;
    private final String PRIORITY_DEFAULT;
    private final String JIRA_HOST;
    private final String authInfo;
    private final String urlJiraFinal;

    private static final String ENT_TRACKERUPDATE_ADDRESS = "support.update.bugtracker";


    public DefaultDemandeServiceImpl(Vertx vertx, Container container, EmailSender emailSender) {
        this.mongo = MongoDb.getInstance();
        this.emailSender = emailSender;
        this.log = container.logger();
        this.vertx = vertx;
        eb = getEventBus(vertx);
        this.MAIL_IWS = container.config().getString("mail-iws");
        this.COLLECTIVITY_DEFAULT = container.config().getString("default-collectivity");
        this.ATTRIBUTION_DEFAULT = container.config().getString("default-attribution");
        this.TICKETTYPE_DEFAULT = container.config().getString("default-tickettype");
        this.PRIORITY_DEFAULT = container.config().getString("default-priority");
        String jiraLogin = container.config().getString("jira-login");
        String jiraPassword = container.config().getString("jira-passwd");
        this.JIRA_HOST = container.config().getString("jira-host");
        String jiraUrl = container.config().getString("jira-url");
        int jiraPort = container.config().getInteger("jira-port");

        // Login & Passwd Jira
        authInfo = jiraLogin + ":" + jiraPassword;
        // Final url Jira
        urlJiraFinal = JIRA_HOST + ":" + jiraPort + jiraUrl;

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
        switch(moduleName) {
            case "actualites": return new String("Actualités".getBytes(), StandardCharsets.UTF_8);
            default: return moduleName;
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
                sendToCGI(jsonPivot, handler);
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
     * Send pivot information from IWS -- to Jira
     * A ticket is created with the Jira API with all the json information received
     * @param jsonPivot JSON in pivot format
     */
    private void sendToCGI(JsonObject jsonPivot, final Handler<Either<String, JsonObject>> handler) {

        final AtomicBoolean responseIsSent = new AtomicBoolean(false);
        final JsonObject jsonJiraTicket = new JsonObject();

        jsonJiraTicket.putObject("fields", new JsonObject()
            .putObject("project", new JsonObject()
                    .putString("key", jsonPivot.getString(Supportpivot.COLLECTIVITY_FIELD)))
            .putString("summary", jsonPivot.getString(Supportpivot.TITLE_FIELD))
            .putString("description", jsonPivot.getString(Supportpivot.DESCRIPTION_FIELD))
            .putObject("issuetype", new JsonObject()
                    .putString("name", jsonPivot.getString(Supportpivot.TICKETTYPE_FIELD)))
            .putArray("labels", jsonPivot.getArray(Supportpivot.MODULES_FIELD))
            //TODO : Les cutoms fields sont à changer selon l environnement JIRA
            .putString("customfield_12600", jsonPivot.getString(Supportpivot.IDENT_FIELD))
            .putString("customfield_12400", jsonPivot.getString(Supportpivot.IDIWS_FIELD))
            .putString("customfield_12602", jsonPivot.getString(Supportpivot.STATUSENT_FIELD))
            .putString("customfield_12601", jsonPivot.getString(Supportpivot.STATUSIWS_FIELD))
            .putString("customfield_12608", jsonPivot.getString(Supportpivot.DATE_CREA_FIELD))
            .putString("customfield_12606", jsonPivot.getString(Supportpivot.DATE_RESOENT_FIELD))
            .putString("customfield_12605", jsonPivot.getString(Supportpivot.DATE_RESOIWS_FIELD))
            .putString("customfield_11405", jsonPivot.getString(Supportpivot.CREATOR_FIELD))
            .putObject("priority", new JsonObject()
                    .putString("name", jsonPivot.getString(Supportpivot.PRIORITY_FIELD))));


        final JsonArray jsonJiraComments = jsonPivot.getArray(Supportpivot.COMM_FIELD);


        log.debug("Ticket content : " + jsonJiraTicket);

        // Create ticket via Jira API

        URI jira_URI;
        try {
            jira_URI = new URI(urlJiraFinal);
        } catch (URISyntaxException e) {
            log.error("Invalid jira web service uri", e);
            handler.handle(new Either.Left<String, JsonObject>("Invalid jira url"));
            return;
        }

        final HttpClient httpClient = generateHttpClient(jira_URI);

        final HttpClientRequest httpClientRequest = httpClient.post(urlJiraFinal , getJiraUpdateHandler(jsonJiraComments, handler))
                .putHeader(HttpHeaders.HOST, JIRA_HOST)
                .putHeader("Authorization", "Basic " + Base64.encodeBytes(authInfo.getBytes()))
                .putHeader("Content-Type", "application/json")
                .setChunked(true)
                .write(jsonJiraTicket.encode());

        if (!responseIsSent.getAndSet(true)) {
            httpClient.close();
        }

        httpClientRequest.end();
        log.debug("End HttpClientRequest to create jira ticket");

    }

    private Handler<HttpClientResponse> getJiraUpdateHandler(final JsonArray jsonJiraComments,
                                                             final Handler<Either<String, JsonObject>> handler) {
        return new Handler<HttpClientResponse>() {

            @Override
            public void handle(HttpClientResponse response) {

                // HTTP Status Code 201: The request has been fulfilled and has resulted in one or more new resources being created.
                if (response.statusCode() == 201) {

                    response.bodyHandler(new Handler<Buffer>() {
                        @Override
                        public void handle(Buffer buffer) {

                            JsonObject infoNewJiraTicket = new JsonObject(buffer.toString());
                            String idNewJiraTicket = infoNewJiraTicket.getString("key");

                            log.debug("JIRA ticket Informations : " + infoNewJiraTicket);
                            log.debug("JIRA ticket ID created : " + idNewJiraTicket);

                            LinkedList<String> commentsLinkedList = new LinkedList<>();

                            if ( jsonJiraComments != null ) {
                                for( Object comment : jsonJiraComments ) {
                                    commentsLinkedList.add(comment.toString());
                                }
                                sendJiraComments( idNewJiraTicket, commentsLinkedList, handler );
                            }
                            else {
                                handler.handle(new Either.Right<String, JsonObject>(new JsonObject().putString("status", "OK")));
                            }
                        }
                    });

                } else {
                    log.error("Error when calling URL : " + response.statusMessage());
                    handler.handle(new Either.Left<String, JsonObject>("Error when creating Jira ticket"));
                }
            }
        };
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
            .append("<br />academie = ")
            .append(jsonPivot.getString(Supportpivot.ACADEMY_FIELD, ""))
            .append("<br />demandeur = ")
            .append(jsonPivot.getString(Supportpivot.CREATOR_FIELD))
            .append("<br />type_demande = ")
            .append(jsonPivot.getString(Supportpivot.TICKETTYPE_FIELD, ""))
            .append("<br />titre = ")
            .append(jsonPivot.getString(Supportpivot.TITLE_FIELD))
            .append("<br />description = ")
            .append(jsonPivot.getString(Supportpivot.DESCRIPTION_FIELD))
            .append("<br />priorite = ")
            .append(jsonPivot.getString(Supportpivot.PRIORITY_FIELD, ""))
            .append("<br />id_jira = ")
            .append(jsonPivot.getString(Supportpivot.IDJIRA_FIELD, ""))
            .append("<br />id_ent = ")
            .append(jsonPivot.getString(Supportpivot.IDENT_FIELD))
            .append("<br />id_iws = ")
            .append(jsonPivot.getString(Supportpivot.IDIWS_FIELD, ""));

        JsonArray comm = jsonPivot.getArray(Supportpivot.COMM_FIELD, new JsonArray());
        for(int i=0 ; i<comm.size();i++){
            mail.append("<br />commentaires = ")
                    .append((String)comm.get(i));
        }

        JsonArray modules =   jsonPivot.getArray(Supportpivot.MODULES_FIELD, new JsonArray());
        mail.append("<br />modules = ");
        for(int i=0 ; i<modules.size();i++){
            if(i > 0) {
                mail.append(", ");
            }
            mail.append((String)modules.get(i));
        }
        mail.append("<br />statut_iws = ")
            .append(jsonPivot.getString(Supportpivot.STATUSIWS_FIELD, ""))
            .append("<br />statut_ent = ")
            .append(jsonPivot.getString(Supportpivot.STATUSENT_FIELD, ""))
            .append("<br />statut_jira = ")
            .append(jsonPivot.getString(Supportpivot.STATUSJIRA_FIELD, ""))
            .append("<br />date_creation = ")
            .append(jsonPivot.getString(Supportpivot.DATE_CREA_FIELD, ""))
            .append("<br />date_resolution_iws = ")
            .append(jsonPivot.getString(Supportpivot.DATE_RESOIWS_FIELD, ""))
            .append("<br />date_resolution_ent = ")
            .append(jsonPivot.getString(Supportpivot.DATE_RESOENT_FIELD, ""))
            .append("<br />date_resolution_jira = ")
            .append(jsonPivot.getString(Supportpivot.DATE_RESOJIRA_FIELD, ""))
            .append("<br />reponse_technique = ")
            .append(jsonPivot.getString(Supportpivot.TECHNICAL_RESP_FIELD, ""))
            .append("<br />reponse_client = ")
            .append(jsonPivot.getString(Supportpivot.CLIENT_RESP_FIELD, ""))
            .append("<br />attribution = ")
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
     * Generate HTTP client
     * @param uri uri
     * @return Http client
     */
    private HttpClient generateHttpClient(URI uri) {
        return vertx.createHttpClient()
                .setHost(uri.getHost())
                .setPort((uri.getPort() > 0) ? uri.getPort() : ("https".equals(uri.getScheme()) ? 443 : 80))
                .setVerifyHost(false)
                .setTrustAll(true)
                .setSSL("https".equals(uri.getScheme()))
                .setKeepAlive(false);
    }

    /**
     * Send Jira Comments
     * @param idJira arrayComments
     */
    private void sendJiraComments(final String idJira, final LinkedList commentsLinkedList,
                                  final Handler<Either<String, JsonObject>> handler) {
        final AtomicBoolean responseIsSent = new AtomicBoolean(false);

        if( commentsLinkedList.size() > 0 ) {

            URI jira_add_comment_URI = null;

            final String urlNewTicket = urlJiraFinal + idJira + "/comment";

            try {
                jira_add_comment_URI = new URI(urlNewTicket);
            } catch (URISyntaxException e) {
                log.error("Invalid jira web service uri", e);
                handler.handle(new Either.Left<String, JsonObject>("Invalid jira url : " + urlNewTicket));
            }

            if (jira_add_comment_URI != null) {
                final HttpClient httpClient = generateHttpClient(jira_add_comment_URI);

                final HttpClientRequest httpClientRequest = httpClient.post(urlNewTicket , new Handler<HttpClientResponse>() {
                    @Override
                    public void handle(HttpClientResponse response) {

                        // If ticket well created, then HTTP Status Code 201: The request has been fulfilled and has resulted in one or more new resources being created.
                        if (response.statusCode() != 201) {
                            log.error("Error when calling URL : " + response.statusMessage());
                        }
                        //Recursive call
                        commentsLinkedList.removeFirst();
                        sendJiraComments(idJira, commentsLinkedList, handler);
                    }
                });
                httpClientRequest.putHeader(HttpHeaders.HOST, JIRA_HOST)
                    .putHeader("Authorization", "Basic " + Base64.encodeBytes(authInfo.getBytes()))
                    .putHeader("Content-Type", "application/json")
                    .setChunked(true);

                final JsonObject jsonCommTicket = new JsonObject();
                jsonCommTicket.putString("body", commentsLinkedList.getFirst().toString());
                httpClientRequest.write(jsonCommTicket.encode());

                if (!responseIsSent.getAndSet(true)) {
                    httpClient.close();
                }

                httpClientRequest.end();
                log.debug("End HttpClientRequest to create jira comment");
            }


        } else {
            handler.handle(new Either.Right<String, JsonObject>(new JsonObject().putString("status", "OK")));
        }

    }

}
