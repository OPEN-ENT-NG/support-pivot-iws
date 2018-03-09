package fr.openent.supportpivot.service.impl;

import fr.openent.supportpivot.Supportpivot;
import fr.openent.supportpivot.service.JiraService;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.email.EmailSender;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.*;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.json.impl.Base64;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.platform.Container;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.Format;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Created by mercierq on 09/02/2018.
 * Default implementation for JiraService
 */
public class DefaultJiraServiceImpl implements JiraService {


    private final Logger log;
    private final Vertx vertx;
    private final String JIRA_HOST;
    private final String jiraAuthInfo;
    private final String urlJiraFinal;
    private final String DEFAULT_COLLECTIVITY;
    private final String DEFAULT_ATTRIBUTION;
    private final String DEFAULT_TICKETTYPE;
    private final String DEFAULT_PRIORITY;

    private final JsonObject JIRA_FIELD;


    DefaultJiraServiceImpl(Vertx vertx, Container container, EmailSender emailSender) {

        this.log = container.logger();
        this.vertx = vertx;
        String jiraLogin = container.config().getString("jira-login");
        String jiraPassword = container.config().getString("jira-passwd");
        this.JIRA_HOST = container.config().getString("jira-host");
        String jiraUrl = container.config().getString("jira-url");
        int jiraPort = container.config().getInteger("jira-port");
        JIRA_FIELD = container.config().getObject("jira-custom-fields");
        if(JIRA_FIELD == null) {
            log.fatal("Supportpivot : no jira-custom-fields in configuration");
        }

        jiraAuthInfo = jiraLogin + ":" + jiraPassword;
        urlJiraFinal = JIRA_HOST + ":" + jiraPort + jiraUrl;

        this.DEFAULT_COLLECTIVITY = container.config().getString("default-collectivity");
        this.DEFAULT_ATTRIBUTION = container.config().getString("default-attribution");
        this.DEFAULT_TICKETTYPE = container.config().getString("default-tickettype");
        this.DEFAULT_PRIORITY = container.config().getString("default-priority");


    }


    /**
     * Send pivot information from IWS -- to Jira
     * A ticket is created with the Jira API with all the json information received
     * @param jsonPivot JSON in pivot format
     */
    public void sendToJIRA(JsonObject jsonPivot, final Handler<Either<String, JsonObject>> handler) {

        String ticketType = DEFAULT_TICKETTYPE;
        if (jsonPivot.getString(Supportpivot.TICKETTYPE_FIELD).equals("Anomalie") ||
                jsonPivot.getString(Supportpivot.TICKETTYPE_FIELD).equals("Incident") ||
                jsonPivot.getString(Supportpivot.TICKETTYPE_FIELD).equals("Service")) {
            ticketType = jsonPivot.getString(Supportpivot.TICKETTYPE_FIELD);
        }

        if (!jsonPivot.getString(Supportpivot.IDJIRA_FIELD).isEmpty()) {
            String jiraTicketId = jsonPivot.getString(Supportpivot.IDJIRA_FIELD);
            getJiraTicketContents(jsonPivot, jiraTicketId, handler);
        } else {
            final JsonObject jsonJiraTicket = new JsonObject();

            String currentPriority = jsonPivot.getString(Supportpivot.PRIORITY_FIELD);
            switch(currentPriority) {
                case "Mineur" :
                    currentPriority = "Mineure";
                    break;
                case "Majeur":
                    currentPriority = "Majeure";
                    break;
                case "Bloquant":
                    currentPriority = "Bloquante";
                    break;
                default:
                    currentPriority = DEFAULT_PRIORITY;
                    break;
            }

            String currentCollectivite = jsonPivot.getString(Supportpivot.COLLECTIVITY_FIELD);
            switch(currentCollectivite) {
                case "MDP":
                    currentCollectivite = "NGMDP";
                    break;
                default:
                    currentCollectivite = "NGMDP";
                    break;
            }

            jsonJiraTicket.putObject("fields", new JsonObject()
                    .putObject("project", new JsonObject()
                            .putString("key", currentCollectivite))
                    .putString("summary", jsonPivot.getString(Supportpivot.TITLE_FIELD))
                    .putString("description", jsonPivot.getString(Supportpivot.DESCRIPTION_FIELD))
                    .putObject("issuetype", new JsonObject()
                            .putString("name", ticketType))
                    .putArray("labels", jsonPivot.getArray(Supportpivot.MODULES_FIELD))
                    .putString(JIRA_FIELD.getString("id_ent"), jsonPivot.getString(Supportpivot.IDENT_FIELD))
                    .putString(JIRA_FIELD.getString("id_iws"), jsonPivot.getString(Supportpivot.IDIWS_FIELD))
                    .putString(JIRA_FIELD.getString("status_ent"), jsonPivot.getString(Supportpivot.STATUSENT_FIELD))
                    .putString(JIRA_FIELD.getString("status_iws"), jsonPivot.getString(Supportpivot.STATUSIWS_FIELD))
                    .putString(JIRA_FIELD.getString("creation"), jsonPivot.getString(Supportpivot.DATE_CREA_FIELD))
                    .putString(JIRA_FIELD.getString("resolution_ent"), jsonPivot.getString(Supportpivot.DATE_RESOENT_FIELD))
                    .putString(JIRA_FIELD.getString("resolution_iws"), jsonPivot.getString(Supportpivot.DATE_RESOIWS_FIELD))
                    .putString(JIRA_FIELD.getString("creator"), jsonPivot.getString(Supportpivot.CREATOR_FIELD))
                    .putObject("priority", new JsonObject()
                            .putString("name", currentPriority)));

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

            final HttpClientRequest httpClientRequest = httpClient.post(urlJiraFinal, getJiraUpdateHandler(jsonJiraComments, handler))
                    .putHeader(HttpHeaders.HOST, JIRA_HOST)
                    .putHeader("Authorization", "Basic " + Base64.encodeBytes(jiraAuthInfo.getBytes()))
                    .putHeader("Content-Type", "application/json")
                    .setChunked(true)
                    .write(jsonJiraTicket.encode());

            httpClient.close();
            httpClientRequest.end();
            log.debug("End HttpClientRequest to create jira ticket");
        }
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
                                sendJiraComments( idNewJiraTicket, commentsLinkedList, handler);
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
     * Send Jira Comments
     * @param idJira arrayComments
     */
    private void sendJiraComments(final String idJira, final LinkedList commentsLinkedList,
                                  final Handler<Either<String, JsonObject>> handler) {

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
                        .putHeader("Authorization", "Basic " + Base64.encodeBytes(jiraAuthInfo.getBytes()))
                        .putHeader("Content-Type", "application/json")
                        .setChunked(true);

                final JsonObject jsonCommTicket = new JsonObject();
                jsonCommTicket.putString("body", commentsLinkedList.getFirst().toString());
                httpClientRequest.write(jsonCommTicket.encode());

                httpClient.close();
                httpClientRequest.end();
                log.debug("End HttpClientRequest to create jira comment");
            }


        } else {
            handler.handle(new Either.Right<String, JsonObject>(new JsonObject().putString("status", "OK")));
        }

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
     * Get general ticket information via Jira API
     */
    private void getJiraTicketContents(final JsonObject jsonPivot, final String jiraTicketId,
                                       final Handler<Either<String, JsonObject>> handler) {

        URI jira_get_infos_URI = null;
        final String urlGetTicketGeneralInfo = urlJiraFinal + jiraTicketId ;

        try {
            jira_get_infos_URI = new URI(urlGetTicketGeneralInfo);
        } catch (URISyntaxException e) {
            log.error("Invalid jira web service uri", e);
            handler.handle(new Either.Left<String, JsonObject>("Invalid jira url : " + urlGetTicketGeneralInfo));
        }

        final HttpClient httpClient = generateHttpClient(jira_get_infos_URI);

        final HttpClientRequest httpClientRequestGetInfo = httpClient.get(urlGetTicketGeneralInfo, new Handler<HttpClientResponse>() {
            @Override
            public void handle(HttpClientResponse response) {
                processJiraInfo(response, jsonPivot, jiraTicketId, handler);
            }
        });

        httpClientRequestGetInfo.putHeader("Authorization", "Basic " + Base64.encodeBytes(jiraAuthInfo.getBytes()));
        httpClient.close();
        httpClientRequestGetInfo.end();

    }

    private void processJiraInfo(HttpClientResponse response, final JsonObject jsonPivot, final String jiraTicketId,
                                 final Handler<Either<String, JsonObject>> handler) {
        if (response.statusCode() == 200) {
            response.bodyHandler(new Handler<Buffer>() {
                @Override
                public void handle(Buffer bufferGetInfosTicket) {
                    JsonObject jsonGetInfosTicket = new JsonObject(bufferGetInfosTicket.toString());
                    updateJiraInformations(jiraTicketId, jsonPivot, jsonGetInfosTicket, handler);
                }
            });
        } else {
            log.error("Error when calling URL : " + response.statusMessage());
            handler.handle(new Either.Left<String, JsonObject>("Error when getting Jira ticket information"));
        }
    }


    /**
     * Update ticket information via Jira API
     */
    private void updateJiraInformations (final String jiraTicketId, final JsonObject jsonPivot, final JsonObject jsonCurrentTicketInfos,
                                         final Handler<Either<String, JsonObject>> handler) {

        final String urlUpdateJiraTicket = urlJiraFinal + jiraTicketId;

        final JsonObject jsonJiraUpdateTicket = new JsonObject();
        jsonJiraUpdateTicket.putObject("fields", new JsonObject()
                .putString(JIRA_FIELD.getString("status_ent"), jsonPivot.getString(Supportpivot.STATUSENT_FIELD))
                .putString(JIRA_FIELD.getString("status_iws"), jsonPivot.getString(Supportpivot.STATUSIWS_FIELD))
                .putString(JIRA_FIELD.getString("resolution_ent"), jsonPivot.getString(Supportpivot.DATE_RESOENT_FIELD))
                .putString(JIRA_FIELD.getString("resolution_iws"), jsonPivot.getString(Supportpivot.DATE_RESOIWS_FIELD)));

        URI jira_update_infos_URI;
        try {
            jira_update_infos_URI = new URI(urlUpdateJiraTicket);
        } catch (URISyntaxException e) {
            log.error("Invalid jira web service uri", e);
            handler.handle(new Either.Left<String, JsonObject>("Invalid jira url"));
            return;
        }

        final HttpClient httpClient = generateHttpClient(jira_update_infos_URI);


        final HttpClientRequest httpClientRequest = httpClient.put(urlUpdateJiraTicket , new Handler<HttpClientResponse>() {
            @Override
            public void handle(HttpClientResponse response) {
                if (response.statusCode() == 204) {

                    // Compare comments and add only new ones
                    JsonArray jsonPivotTicketComments = jsonPivot.getArray("commentaires");
                    JsonArray jsonCurrentTicketComments = jsonCurrentTicketInfos.getObject("fields").getObject("comment").getArray("comments");

                    JsonArray newComments = compareComments(jsonCurrentTicketComments, jsonPivotTicketComments);

                    LinkedList<String> commentsLinkedList = new LinkedList<>();

                    if ( newComments != null ) {
                        for( Object comment : newComments ) {
                            commentsLinkedList.add(comment.toString());
                        }
                        sendJiraComments(jiraTicketId, commentsLinkedList, handler);
                    }
                    else {
                        handler.handle(new Either.Right<String, JsonObject>(new JsonObject().putString("status", "OK")));
                    }

                }
                else {
                    log.error("Error when calling URL : " + response.statusMessage());
                    handler.handle(new Either.Left<String, JsonObject>("Error when update Jira ticket information"));
                }
            }
        });

        httpClientRequest.putHeader(HttpHeaders.HOST, JIRA_HOST)
                .putHeader("Authorization", "Basic " + Base64.encodeBytes(jiraAuthInfo.getBytes()))
                .putHeader("Content-Type", "application/json")
                .setChunked(true)
                .write(jsonJiraUpdateTicket.encode());

        httpClient.close();
        httpClientRequest.end();
        log.debug("End HttpClientRequest to update jira ticket");

    }



    /**
     * Transform a comment from pivot format, to json
     * @param comment Original full '|' separated string
     * @return JsonFormat with correct metadata (owner and date)
     */
    private JsonObject unserializeComment(String comment) {
        try{
            String[] elements = comment.split(Pattern.quote("|"));
            if(elements.length < 4) {
                return null;
            }
            JsonObject jsonComment = new JsonObject();
            jsonComment.putString("id", elements[0].trim());
            jsonComment.putString("owner", elements[1].trim());
            jsonComment.putString("created", elements[2].trim());
            StringBuilder content = new StringBuilder();
            for(int i = 3; i<elements.length ; i++) {
                content.append(elements[i]);
                content.append("|");
            }
            content.deleteCharAt(content.length() - 1);
            jsonComment.putString("content", content.toString());
            return jsonComment;
        } catch (NullPointerException e) {
            return null;
        }
    }

    /**
     * Format date from SQL format : yyyy-MM-dd'T'HH:mm:ss
     * to pivot comment id format : yyyyMMddHHmmss
     * or display format : yyyy-MM-dd HH:mm:ss
     * @param sqlDate date string to format
     * @return formatted date string
     */
    private String getDateFormatted (final String sqlDate, final boolean idStyle) {
        final DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        //df.setTimeZone(TimeZone.getTimeZone("GMT"));
        Date d;
        try {
            d = df.parse(sqlDate);
        } catch (ParseException e) {
            log.error("Support : error when parsing date");
            e.printStackTrace();
            return "iderror";
        }
        Format formatter;
        if(idStyle) {
            formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        } else {
            formatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        }
        return formatter.format(d);
    }

    /**
     * Compare comments of ticket and bugtracker issue.
     * Add every comment to ticket not already existing
     * @param ticketJiraComments comments of Jira ticket
     * @param issuePivotComments comment of Bugtracker issue
     * @return comments that needs to be added in ticket
     */
    private JsonArray compareComments(JsonArray ticketJiraComments, JsonArray issuePivotComments) {
        JsonArray commentsToAdd = new JsonArray();
        for(Object oi : issuePivotComments)  {
            if( !(oi instanceof String) ) continue;
            String rawComment = (String)oi;
            JsonObject issueComment = unserializeComment(rawComment);
            String issueCommentId;

            if(issueComment != null && issueComment.containsField("id")) {
                issueCommentId = issueComment.getString("id", "");
            } else {
                log.error("Support : Invalid comment : " + rawComment);
                continue;
            }

            boolean existing = false;
            for(Object ot : ticketJiraComments) {
                if( !(ot instanceof JsonObject) ) continue;
                JsonObject ticketComment = (JsonObject)ot;
                String ticketCommentCreated = ticketComment.getString("created","").trim();
                String ticketCommentId = getDateFormatted(ticketCommentCreated, true);
                String ticketCommentContent = ticketComment.getString("body", "").trim();
                JsonObject ticketCommentPivotContent = unserializeComment(ticketCommentContent);

                String ticketCommentPivotId = "";
                if( ticketCommentPivotContent != null ) {
                    ticketCommentPivotId = ticketCommentPivotContent.getString("id");
                }
                if(issueCommentId.equals(ticketCommentId)
                        || issueCommentId.equals(ticketCommentPivotId)) {
                    existing = true;
                    break;
                }
            }
            if(!existing) {
                commentsToAdd.addString(rawComment);
            }
        }
        return commentsToAdd;
    }




    /**
     * Send ticket informations to IWS when a Jira ticket has been modified
     * @param jiraTicketId Jira ticket ID received from REST JIRA when ticket updated
     */
    public void getTicketUpdatedToIWS (final HttpServerRequest request, final String jiraTicketId,
                                         final Handler<Either<String, JsonObject>> handler) {

        URI jira_get_infos_URI = null;
        final String urlGetTicketGeneralInfo = urlJiraFinal + jiraTicketId ;

        try {
            jira_get_infos_URI = new URI(urlGetTicketGeneralInfo);
        } catch (URISyntaxException e) {
            log.error("Invalid jira web service uri", e);
            handler.handle(new Either.Left<String, JsonObject>("Invalid jira url : " + urlGetTicketGeneralInfo));
        }

        final HttpClient httpClient = generateHttpClient(jira_get_infos_URI);

        final HttpClientRequest httpClientRequestGetInfo = httpClient.get(urlGetTicketGeneralInfo, new Handler<HttpClientResponse>() {
            @Override
            public void handle(HttpClientResponse response) {

                if (response.statusCode() == 200) {
                    response.bodyHandler(new Handler<Buffer>() {
                        @Override
                        public void handle(Buffer bufferGetInfosTicket) {
                            JsonObject jsonGetInfosTicket = new JsonObject(bufferGetInfosTicket.toString());
                            modifiedJiraJsonToIWS(request, jsonGetInfosTicket, handler);
                        }
                    });
                } else {
                    log.error("Error when calling URL : " + response.statusMessage());
                    handler.handle(new Either.Left<String, JsonObject>("Error when getting Jira ticket information"));
                }
            }
        });

        httpClientRequestGetInfo.putHeader("Authorization", "Basic " + Base64.encodeBytes(jiraAuthInfo.getBytes()));
        httpClient.close();
        httpClientRequestGetInfo.end();

    }


    /**
     * Modified Jira JSON to prepare to send the email to IWS
     */
    private void modifiedJiraJsonToIWS(HttpServerRequest request, final JsonObject jsonJiraTicketInfos,
                                           final Handler<Either<String, JsonObject>> handler) {


        if  (jsonJiraTicketInfos.getObject("fields").containsField(JIRA_FIELD.getString("id_iws"))
            && jsonJiraTicketInfos.getObject("fields").getString(JIRA_FIELD.getString("id_iws")) != null) {
                jsonJiraTicketToIWS(request, jsonJiraTicketInfos, handler);
            } else {
                handler.handle(new Either.Left<String, JsonObject>("Field " + JIRA_FIELD.getString("id_iws") + " does not exist."));
            }
    }



    /**
     * Create json pivot format information from JIRA to IWS -- by mail
     * Every data must be htmlEncoded because of encoding / mails incompatibility
     * @param jiraTicket JSON in JIRA format
     */
    private void jsonJiraTicketToIWS(HttpServerRequest request, JsonObject jiraTicket, final Handler<Either<String, JsonObject>> handler) {

        JsonObject jsonPivot = new JsonObject();

        String currentCollectivite = jiraTicket.getObject("fields").getObject("project").getString("name");
        switch(currentCollectivite) {
            case "NGMDP":
                currentCollectivite = DEFAULT_COLLECTIVITY;
                break;
            default:
                currentCollectivite = DEFAULT_COLLECTIVITY;
                break;
        }

        jsonPivot.putString(Supportpivot.COLLECTIVITY_FIELD, currentCollectivite);

        jsonPivot.putString(Supportpivot.ACADEMY_FIELD, "PARIS");


        if  (jiraTicket.getObject("fields").getString(JIRA_FIELD.getString("creator")) != null) {
            jsonPivot.putString(Supportpivot.CREATOR_FIELD,
                    jiraTicket.getObject("fields").getString(stringEncode(JIRA_FIELD.getString("creator"))));
        } else {
            jsonPivot.putString(Supportpivot.CREATOR_FIELD, "");
        }

        jsonPivot.putString(Supportpivot.TICKETTYPE_FIELD, jiraTicket.getObject("fields")
                        .getObject("issuetype").getString("name"))
                .putString(Supportpivot.TITLE_FIELD,
                        jiraTicket.getObject("fields").getString("summary"));

        if  (jiraTicket.getObject("fields").getString("description") != null) {
            jsonPivot.putString(Supportpivot.DESCRIPTION_FIELD,
                    jiraTicket.getObject("fields").getString("description"));
        } else {
            jsonPivot.putString(Supportpivot.DESCRIPTION_FIELD, "");
        }


        String currentPriority = jiraTicket.getObject("fields").getObject("priority").getString("name");
        switch(currentPriority) {
            case "Lowest":
            case "Mineure":
                currentPriority = "Mineur";
                break;
            case "High":
            case "Majeure":
                currentPriority = "Majeur";
                break;
            case "Highest":
            case "Bloquante":
                currentPriority = "Bloquant";
                break;
            default:
                currentPriority = "Mineur";
                break;
        }

        jsonPivot.putString(Supportpivot.PRIORITY_FIELD, currentPriority);

        jsonPivot.putArray(Supportpivot.MODULES_FIELD,
                jiraTicket.getObject("fields").getArray("labels")).putString(Supportpivot.IDJIRA_FIELD, jiraTicket.getString("key"));

        if  (jiraTicket.getObject("fields").getString(JIRA_FIELD.getString("id_ent")) != null) {
            jsonPivot.putString(Supportpivot.IDENT_FIELD,
                    jiraTicket.getObject("fields").getString(JIRA_FIELD.getString("id_ent")));
        } else {
            jsonPivot.putString(Supportpivot.IDENT_FIELD, "");
        }

        if  (jiraTicket.getObject("fields").getString(JIRA_FIELD.getString("id_iws")) != null) {
            jsonPivot.putString(Supportpivot.IDIWS_FIELD,
                    jiraTicket.getObject("fields").getString(JIRA_FIELD.getString("id_iws")));
        }

        JsonArray comm = jiraTicket.getObject("fields").getObject("comment")
                .getArray("comments", new JsonArray());
        JsonArray jsonCommentArray = new JsonArray();
        for(int i=0 ; i<comm.size();i++){
            JsonObject comment = comm.get(i);
            String commentFormated = serializeComment(comment);
            jsonCommentArray.addString(commentFormated);
        }
        jsonPivot.putArray(Supportpivot.COMM_FIELD, jsonCommentArray);


        if  (jiraTicket.getObject("fields").getString(JIRA_FIELD.getString("status_ent")) != null) {
            jsonPivot.putString(Supportpivot.STATUSENT_FIELD,
                    jiraTicket.getObject("fields").getString(JIRA_FIELD.getString("status_ent")));
        }
        if  (jiraTicket.getObject("fields").getString(JIRA_FIELD.getString("status_iws")) != null) {
            jsonPivot.putString(Supportpivot.STATUSIWS_FIELD,
                    jiraTicket.getObject("fields").getString(JIRA_FIELD.getString("status_iws")));
        }

        jsonPivot.putString(Supportpivot.STATUSJIRA_FIELD,
                jiraTicket.getObject("fields").getObject("status").getString("name"));


        if  (jiraTicket.getObject("fields").getString(JIRA_FIELD.getString("creation")) != null) {
            jsonPivot.putString(Supportpivot.DATE_CREA_FIELD,
                    jiraTicket.getObject("fields").getString(JIRA_FIELD.getString("creation")));
        }

        if  (jiraTicket.getObject("fields").getString(JIRA_FIELD.getString("resolution_iws")) != null) {
            jsonPivot.putString(Supportpivot.DATE_RESOIWS_FIELD,
                    jiraTicket.getObject("fields").getString(JIRA_FIELD.getString("resolution_iws")));
        }

        if  (jiraTicket.getObject("fields").getString(JIRA_FIELD.getString("resolution_ent")) != null) {
            jsonPivot.putString(Supportpivot.DATE_RESOENT_FIELD,
                    jiraTicket.getObject("fields").getString(JIRA_FIELD.getString("resolution_ent")));
        }

        if  (jiraTicket.getObject("fields").getString("resolutiondate") != null) {
            jsonPivot.putString(Supportpivot.DATE_RESOJIRA_FIELD,
                    jiraTicket.getObject("fields").getString("resolutiondate"));
        }

        jsonPivot.putString(Supportpivot.ATTRIBUTION_FIELD, "IWS");

        handler.handle(new Either.Right<String, JsonObject>(jsonPivot));
    }


    /**
     * Serialize comments : date | author | content
     * @param comment Json Object with a comment to serialize
     * @return String with comment serialized
     */
    private String serializeComment (final JsonObject comment) {
        String content = getDateFormatted(comment.getString("created"), true)
                + " | " + comment.getObject("author").getString("displayName")
                + " | " + getDateFormatted(comment.getString("created"), false)
                + " | " + comment.getString("body");

        String origContent = comment.getString("body");

        return hasToSerialize(origContent) ? content : origContent;
    }

    /**
     * Check if comment must be serialized
     * If it's '|' separated (at least 4 fields)
     * And first field is 14 number (AAAMMJJHHmmSS)
     * Then it must not be serialized
     * @param content Comment to check
     * @return true if the comment has to be serialized
     */
    private boolean hasToSerialize(String content) {
        String[] elements = content.split(Pattern.quote("|"));
        if(elements.length < 4) return true;
        String id = elements[0].trim();
        return ( !id.matches("[0-9]{14}") );
    }

    /**
     * Encode a string in UTF-8
     * @param in String to encode
     * @return encoded String
     */
    private String stringEncode(String in) {
        return new String(in.getBytes(), StandardCharsets.UTF_8);
    }



    /////////////////////////////////////////////////
    // BELOW CODE IS NOT ACTUALLY USED
    // ONLY FOR AUTOMATIC JIRA TRANSITION CHANGE
    /////////////////////////////////////////////////


    /**
     * Get general ticket information via Jira API
     *
    private void getJiraTicketInformations(final JsonObject jsonPivot, final String jiraTicketId, final String iwsTicketStatus,
                                  final Handler<Either<String, JsonObject>> handler) {

        URI jira_get_infos_URI = null;
        final String urlGetTicketGeneralInfo = urlJiraFinal + jiraTicketId ;

        try {
            jira_get_infos_URI = new URI(urlGetTicketGeneralInfo);
        } catch (URISyntaxException e) {
            log.error("Invalid jira web service uri", e);
            handler.handle(new Either.Left<String, JsonObject>("Invalid jira url : " + urlGetTicketGeneralInfo));
        }

        final HttpClient httpClient = generateHttpClient(jira_get_infos_URI);

        final HttpClientRequest httpClientRequestGetInfo = httpClient.get(urlGetTicketGeneralInfo, new Handler<HttpClientResponse>() {
            @Override
            public void handle(HttpClientResponse response) {

                if (response.statusCode() == 200) {
                    response.bodyHandler(new Handler<Buffer>() {
                        @Override
                        public void handle(Buffer bufferGetInfosTicket) {

                            String iwsTicketStatusModified;

                            if ("En cours".equals(iwsTicketStatus)) {
                                iwsTicketStatusModified = "En cours";
                            } else if ("Résolu / A tester".equals(iwsTicketStatus)) {
                                iwsTicketStatusModified = "Résolu sans livraison";
                            } else if ("Fini".equals(iwsTicketStatus)) {
                                iwsTicketStatusModified = "Fermé";
                            }
                            else {
                                iwsTicketStatusModified = "Nouveau";
                            }


                            JsonObject jsonGetInfosTicket = new JsonObject(bufferGetInfosTicket.toString());
                            String statusNameTicket = jsonGetInfosTicket.getObject("fields")
                                    .getObject("status").getString("name");

                            updateJiraInformations(jsonPivot, jiraTicketId, handler);
                        }
                    });
                } else {
                    log.error("Error when calling URL : " + response.statusMessage());
                    handler.handle(new Either.Left<String, JsonObject>("Error when getting Jira ticket information"));
                }
            }
        });

        httpClientRequestGetInfo.putHeader("Authorization", "Basic " + Base64.encodeBytes(jiraAuthInfo.getBytes()));
        httpClient.close();
        httpClientRequestGetInfo.end();

    }
*/

    /**
     * Get transition ticket information via Jira API
     */
    private void changeJiraTransitionInformations (final JsonObject jsonPivot, final String jiraTicketId,
                                                final String currentJiraStatusNameTicket,
                                                final String iwsTicketStatus,
                                                final JsonObject jsonGetInfosTicket,
                                                final Handler<Either<String, JsonObject>> handler) {


        getTransition(jiraTicketId, new Handler<HttpClientResponse>() {
            @Override
            public void handle(HttpClientResponse response) {
                if (response.statusCode() == 200) {
                    response.bodyHandler(new Handler<Buffer>() {
                        @Override
                        public void handle(Buffer bufferInfosTransitionTicket) {


                            JsonObject jsonGetInfosTransitionTicket = new JsonObject(bufferInfosTransitionTicket.toString());
                            //log.info(jsonGetInfosTransitionTicket.toString());
                            //JsonArray jsonTicketTransitionPossibilities = jsonGetInfosTransitionTicket.getArray("transitions");
                            log.info(jsonGetInfosTransitionTicket);

                            //ICI test test result transitions !
                            //Integer jira_transitionId_Assistance_encours = 11;
                            String jira_transitionId_Assistance_encours = new String ("En cours".getBytes(), StandardCharsets.UTF_8);
                            //Integer jira_transitionId_Assistance_resolu = 31;
                            String jira_transitionName_Assistance_resolu = new String ("Résolu sans livraison".getBytes(), StandardCharsets.UTF_8);
                            //Integer jira_transitionId_Assistance_ferme = 41;
                            String jira_transitionName_Assistance_ferme = new String ("Fermé".getBytes(), StandardCharsets.UTF_8);

                            List<String> transitionList = Arrays.asList(jira_transitionId_Assistance_encours,jira_transitionName_Assistance_resolu,jira_transitionName_Assistance_ferme);

                            log.info("currentJiraStatusNameTicket : " + currentJiraStatusNameTicket);
                            String currentJiraStatusNameTicket = jsonGetInfosTicket.getObject("fields").getObject("status").getString("name");
                            log.info("currentJiraStatusNameTicket : " + currentJiraStatusNameTicket);

                            String iwsTicketStatusEncode = new String (iwsTicketStatus.getBytes(), StandardCharsets.UTF_8);

                            log.info ("TEST --> currentJiraStatus : " + currentJiraStatusNameTicket + " = IWS : " + iwsTicketStatus );

                            if (currentJiraStatusNameTicket.equals(iwsTicketStatusEncode)) {
                                log.info("Status Up to date : OK");
                                handler.handle(new Either.Right<String, JsonObject>(new JsonObject().putString("status", "OK")));
                            }
                            else {

                                log.info("Status different");

                                JsonArray transitions = jsonGetInfosTransitionTicket.getArray("transitions");
                                boolean trouve = false;

                                for (Object o : transitions) {
                                    if (!(o instanceof JsonObject)) continue;
                                    JsonObject transition = (JsonObject) o;

                                    String name = transition.getObject("to").getString("name");

                                    log.info("Current Jira status Name ticket : " + currentJiraStatusNameTicket);
                                    log.info("IWS Status ticket : " + iwsTicketStatus);
                                    log.info("Nom transition recherche : " + name);
                                    log.info(transitionList);

                                    if (transitionList.contains(name)) {
                                        changeJiraTransition(jsonPivot,
                                                jiraTicketId,
                                                name,
                                                jsonGetInfosTransitionTicket,
                                                iwsTicketStatus,
                                                new Handler<HttpClientResponse>() {
                                            @Override
                                            public void handle(HttpClientResponse response) {
                                                handler.handle(new Either.Right<String, JsonObject>(new JsonObject().putString("status", "OK")));
                                            }
                                        });
                                        trouve = true;
                                        break;
                                    }
                                    else {
                                        handler.handle(new Either.Left<String, JsonObject>("Error transition name not found in list"));
                                    }

                                }
                                if (!trouve) {
                                    handler.handle(new Either.Left<String, JsonObject>("Error transition name not found"));
                                }
                                else {
                                    log.info("Transition name trouvé");
                                }
                            }
                        }
                    });
                } else {
                    log.error("Error when calling URL : " + response.statusMessage());
                    handler.handle(new Either.Left<String, JsonObject>("Error when getting Jira ticket information"));
                }
            }
        });
    }

    /**
     * Get the Jira transition ID from Ticket Status Name
     */
    private String getTransitionIdFromName(JsonObject jsonTransitions, String nameToSearch) {

        log.info ("nameToSearch " + nameToSearch);

        JsonArray transitions = jsonTransitions.getArray("transitions");
        for(Object o : transitions) {
            if( !(o instanceof JsonObject)) continue;
            JsonObject transition = (JsonObject)o;
            String id = transition.getString("id");
            String name = transition.getObject("to").getString("name");
            if(nameToSearch.equals(name)) {
                return id;
            }
        }
        return null;
        
    }

    /**
     * Change the Jira transition to change de ticket's Status
     */

    private void changeJiraTransition(final JsonObject jsonPivot,
                                      final String idJira,
                                      final String nextNameTransition,
                                      final JsonObject jsonGetInfosTransitionTicket,
                                      final String iwsTicketStatus,
                                      final Handler<HttpClientResponse> handler) {

        final String urlChangeTransitionTicket = urlJiraFinal + idJira + "/transitions";

        String nextIdTransition = getTransitionIdFromName(jsonGetInfosTransitionTicket, nextNameTransition);

        //Create Json to update Jira Ticket
        final JsonObject jsonJiraTicketTransitionUpdate = new JsonObject();
        final JsonObject comment = new JsonObject()
                .putObject("add", new JsonObject()
                        .putString("body", "contenu a remplir"));
        jsonJiraTicketTransitionUpdate.putObject("update", new JsonObject()
                .putArray("comment", new JsonArray().addObject(comment)));
        jsonJiraTicketTransitionUpdate.putObject("transition", new JsonObject()
                .putString("id", nextIdTransition));


        log.info(urlChangeTransitionTicket);
        log.info(jsonJiraTicketTransitionUpdate);

        URI jira_change_transition_URI;
        try {
            jira_change_transition_URI = new URI(urlChangeTransitionTicket);
        } catch (URISyntaxException e) {
            log.error("Invalid jira web service uri", e);
            //handler.handle(new Either.Left<String, JsonObject>("Invalid jira url"));
            return;
        }

        final HttpClient httpClient = generateHttpClient(jira_change_transition_URI);

        final HttpClientRequest httpClientRequest = httpClient.post(urlChangeTransitionTicket, new Handler<HttpClientResponse>() {
            @Override
            public void handle(HttpClientResponse response) {
                // If ticket well created, then HTTP Status Code 201: The request has been fulfilled and has resulted in one or more new resources being created.
                if (response.statusCode() != 204) {
                    log.error("Error when calling URL : " + response.statusMessage());
                }
                else {
                    //changeJiraTransition(idJira, nextNameTransition, jsonGetInfosTransitionTicket, currentJiraStatusNameTicket, iwsTicketStatus, handler);
                    /*
                    changeJiraTransitionInformations(idJira, currentJiraStatusNameTicket, iwsTicketStatus, jsonGetInfosTransitionTicket, new Handler<Either<String, JsonObject>>() {
                        @Override
                        public void handle(Either<String, JsonObject> stringJsonObjectEither) {

                        }
                    });*/

                    //getJiraTicketInformations(jsonPivot, idJira, iwsTicketStatus, new Handler<Either<String, JsonObject>>() {
                        //@Override
                        //public void handle(Either<String, JsonObject> stringJsonObjectEither) {

                        //}
                    //});
                }
            }
        });
        httpClientRequest.putHeader(HttpHeaders.HOST, JIRA_HOST)
                .putHeader("Authorization", "Basic " + Base64.encodeBytes(jiraAuthInfo.getBytes()))
                .putHeader("Content-Type", "application/json")
                .setChunked(true)
                .write(jsonJiraTicketTransitionUpdate.encode());

        httpClient.close();
        httpClientRequest.end();
        log.debug("End HttpClientRequest to create jira ticket");

    }


    private void getTransition (final String jiraTicketId, final Handler<HttpClientResponse> handler ){

        URI jira_get_infos_transition_URI = null;
        final String urlGetTicketInfoTransition = urlJiraFinal + jiraTicketId + "/transitions?expand=transitions.fields";

        try {
            jira_get_infos_transition_URI = new URI(urlGetTicketInfoTransition);
        } catch (URISyntaxException e) {
            log.error("Invalid jira web service uri", e);
            // handler.handle(new Either.Left<String, JsonObject>("Invalid jira url : " + urlGetTicketInfoTransition));
        }

        final HttpClient httpClient = generateHttpClient(jira_get_infos_transition_URI);

        final HttpClientRequest httpClientRequestGetTI = httpClient.get(urlGetTicketInfoTransition, handler);

        httpClientRequestGetTI.putHeader("Authorization", "Basic " + Base64.encodeBytes(jiraAuthInfo.getBytes()));
        httpClient.close();
        httpClientRequestGetTI.end();

    }


}