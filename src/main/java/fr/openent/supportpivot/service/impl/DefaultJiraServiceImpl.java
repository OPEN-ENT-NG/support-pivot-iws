package fr.openent.supportpivot.service.impl;

import fr.openent.supportpivot.Supportpivot;
import fr.openent.supportpivot.service.JiraService;
import fr.wseduc.webutils.Either;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.*;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.json.impl.Base64;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.platform.Container;
import static java.nio.charset.StandardCharsets.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.Format;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
    private final String DEFAULT_TICKETTYPE;
    private final String DEFAULT_PRIORITY;

    private final JsonObject JIRA_FIELD;
    private final JsonObject JIRA_MDP_STATUS;


    DefaultJiraServiceImpl(Vertx vertx, Container container) {

        this.log = container.logger();
        this.vertx = vertx;
        String jiraLogin = container.config().getString("jira-login");
        String jiraPassword = container.config().getString("jira-passwd");
        this.JIRA_HOST = container.config().getString("jira-host");
        String jiraUrl = container.config().getString("jira-url");
        int jiraPort = container.config().getInteger("jira-port");
        JIRA_FIELD = container.config().getObject("jira-custom-fields");
        JIRA_MDP_STATUS = container.config().getObject("collection-MDP").getObject("statutsJira");
        if(JIRA_FIELD == null) {
            log.fatal("Supportpivot : no jira-custom-fields in configuration");
        }

        jiraAuthInfo = jiraLogin + ":" + jiraPassword;
        urlJiraFinal = JIRA_HOST + ":" + jiraPort + jiraUrl;

        this.DEFAULT_COLLECTIVITY = container.config().getString("default-collectivity");
        this.DEFAULT_TICKETTYPE = container.config().getString("default-tickettype");
        this.DEFAULT_PRIORITY = container.config().getString("default-priority");

    }


    /**
     * Send pivot information from IWS -- to Jira
     * A ticket is created with the Jira API with all the json information received
     * @param jsonPivot JSON in pivot format
     */
    public void sendToJIRA(final JsonObject jsonPivot, final Handler<Either<String, JsonObject>> finalHandler) {

        String ticketType = DEFAULT_TICKETTYPE;
        if (jsonPivot.containsField(Supportpivot.TICKETTYPE_FIELD)
            && ("Anomalie".equals(jsonPivot.getString(Supportpivot.TICKETTYPE_FIELD)) ||
                "Incident".equals(jsonPivot.getString(Supportpivot.TICKETTYPE_FIELD)) ||
                "Service".equals(jsonPivot.getString(Supportpivot.TICKETTYPE_FIELD)))) {
            ticketType = jsonPivot.getString(Supportpivot.TICKETTYPE_FIELD);
        }

        if (jsonPivot.containsField(Supportpivot.IDJIRA_FIELD)
            && !jsonPivot.getString(Supportpivot.IDJIRA_FIELD).isEmpty()) {
            String jiraTicketId = jsonPivot.getString(Supportpivot.IDJIRA_FIELD);
            getJiraTicketContents(jsonPivot, jiraTicketId, finalHandler);
        } else {
            final JsonObject jsonJiraTicket = new JsonObject();

            String currentPriority = jsonPivot.getString(Supportpivot.PRIORITY_FIELD, "");
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

            final JsonArray jsonJiraPJ = jsonPivot.getArray(Supportpivot.ATTACHMENT_FIELD);

            log.debug("Ticket content : " + jsonJiraTicket);

            // Create ticket via Jira API

            URI jira_URI;
            try {
                jira_URI = new URI(urlJiraFinal);
            } catch (URISyntaxException e) {
                log.error("Invalid jira web service uri sendToJIRA", e);
                finalHandler.handle(new Either.Left<String, JsonObject>("Invalid jira url"));
                return;
            }

            final HttpClient httpClient = generateHttpClient(jira_URI);

            final HttpClientRequest httpClientRequest = httpClient.post(urlJiraFinal,
                    new Handler<HttpClientResponse>() {
                        @Override
                        public void handle(HttpClientResponse response) {
                            updateComments(response, jsonPivot, jsonJiraComments,
                                    new Handler<Either<String, JsonObject>>() {
                                        @Override
                                        public void handle(Either<String, JsonObject> EitherCommentaires) {
                                            if (EitherCommentaires.isRight()) {
                                                JsonObject jsonPivotCompleted = EitherCommentaires.right().getValue().getObject("jsonPivotCompleted");
                                                updateJiraPJ(jsonPivotCompleted, jsonJiraPJ, jsonPivot, finalHandler);
                                            }
                                            else {
                                                finalHandler.handle(new Either.Left<String, JsonObject>(
                                                        "Error, when creating comments."));
                                            }
                                        }
                                    });
                        }
                    });


            httpClientRequest.putHeader(HttpHeaders.HOST, JIRA_HOST)
                    .putHeader("Authorization", "Basic " + Base64.encodeBytes(jiraAuthInfo.getBytes()))
                    .putHeader("Content-Type", "application/json")
                    .setChunked(true)
                    .write(jsonJiraTicket.encode());

            httpClient.close();
            httpClientRequest.end();
            log.debug("End HttpClientRequest to create jira ticket");
        }
    }

    private void updateComments(final HttpClientResponse response, final JsonObject jsonPivot,
                                 final JsonArray jsonJiraComments,
                                 final Handler<Either<String, JsonObject>> handler) {

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

                    jsonPivot.putString("id_jira", idNewJiraTicket);
                    jsonPivot.putString("statut_jira", "Nouveau");

                    if ( jsonJiraComments != null ) {
                        for( Object comment : jsonJiraComments ) {
                            commentsLinkedList.add(comment.toString());
                        }
                        sendJiraComments(idNewJiraTicket, commentsLinkedList, jsonPivot, handler);
                    }
                    else {
                        handler.handle(new Either.Right<String, JsonObject>(new JsonObject()
                                .putString("status", "OK")
                                .putObject("jsonPivotCompleted", jsonPivot)
                        ));
                    }

                }
            });

        } else {
            log.error("Error when calling URL : " + response.statusMessage() + ". Error when creating Jira ticket.");
            handler.handle(new Either.Left<String, JsonObject>("Error when creating Jira ticket"));
        }
    }


    /**
     * Send Jira Comments
     * @param idJira arrayComments
     */
    private void sendJiraComments(final String idJira, final LinkedList commentsLinkedList, final JsonObject jsonPivot,
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
                            log.error("Error when calling URL : " + response.statusMessage() +
                                    ". Comment has not been posted");
                        }
                        //Recursive call
                        commentsLinkedList.removeFirst();
                        sendJiraComments(idJira, commentsLinkedList, jsonPivot, handler);
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
            handler.handle(new Either.Right<String, JsonObject>(new JsonObject()
                    .putString("status", "OK")
                    .putObject("jsonPivotCompleted", jsonPivot)
            ));
        }

    }



    /**
     * Send PJ from IWS to JIRA
     * @param jsonPivotCompleted jsonJiraPJ jsonPivot handler
     */
    private void updateJiraPJ(final JsonObject jsonPivotCompleted,
                            final JsonArray jsonJiraPJ,
                            final JsonObject jsonPivot,
                            final Handler<Either<String, JsonObject>> handler) {

        String idJira = jsonPivotCompleted.getString(Supportpivot.IDJIRA_FIELD);

        LinkedList<JsonObject> pjLinkedList = new LinkedList<>();

        if ( jsonJiraPJ != null && jsonJiraPJ.size() > 0 ) {
            for( Object o : jsonJiraPJ ) {
                if (!(o instanceof JsonObject)) continue;
                JsonObject pj = (JsonObject) o;
                pjLinkedList.add(pj);
            }
            sendJiraPJ(idJira, pjLinkedList, jsonPivotCompleted, handler);
        }
        else {
            handler.handle(new Either.Right<String, JsonObject>(new JsonObject()
                    .putString("status", "OK")
                    .putObject("jsonPivotCompleted", jsonPivot)
            ));
        }

    }


    /**
     * Send Jira PJ
     * @param idJira, pjLinkedList, jsonPivot, jsonPivotCompleted, handler
     */
    private void sendJiraPJ(final String idJira,
                                final LinkedList<JsonObject> pjLinkedList,
                                final JsonObject jsonPivotCompleted,
                                final Handler<Either<String, JsonObject>> handler) {

        if( pjLinkedList.size() > 0 ) {

            URI jira_add_pj_URI = null;

            final String urlNewTicket = urlJiraFinal + idJira + "/attachments";

            try {
                jira_add_pj_URI = new URI(urlNewTicket);
            } catch (URISyntaxException e) {
                log.error("Invalid jira web service uri", e);
                handler.handle(new Either.Left<String, JsonObject>("Invalid jira url : " + urlNewTicket));
            }

            if (jira_add_pj_URI != null) {
                final HttpClient httpClient = generateHttpClient(jira_add_pj_URI);

                final HttpClientRequest httpClientRequest = httpClient.post(urlNewTicket , new Handler<HttpClientResponse>() {
                    @Override
                    public void handle(HttpClientResponse response) {

                        if (response.statusCode() != 200) {
                            log.error("Error when calling URL : " + response.statusMessage() +
                                    ". Attachment file has not been uploaded");
                        }
                        //Recursive call
                        pjLinkedList.removeFirst();
                        sendJiraPJ(idJira, pjLinkedList, jsonPivotCompleted, handler);

                    }
                });

                String currentBoundary = generateBoundary();

                httpClientRequest.putHeader(HttpHeaders.HOST, JIRA_HOST)
                        .putHeader("X-Atlassian-Token", "no-check")
                        .putHeader("Authorization", "Basic " + Base64.encodeBytes(jiraAuthInfo.getBytes()))
                        .putHeader("Content-Type", "multipart/form-data; boundary=" + currentBoundary);

                String debRequest = "--" + currentBoundary + "\r\n" +
                        "Content-Type: application/octet-stream\r\n" +
                        "Content-Disposition: form-data; name=\"file\"; filename=\"" +
                        pjLinkedList.getFirst().getString("nom")
                        + "\"\r\n\r\n";

                String finRequest = "\r\n--" + currentBoundary + "--";

                byte[] debBytes = debRequest.getBytes();
                byte[] pjBytes = Base64.decode(pjLinkedList.getFirst().getString("contenu"));
                byte[] finBytes = finRequest.getBytes();

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
                try {
                    outputStream.write(debBytes);
                    outputStream.write(pjBytes);
                    outputStream.write(finBytes);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                byte all[] = outputStream.toByteArray();

                httpClientRequest.putHeader("Content-Length", all.length + "")
                        .write(new Buffer(all))
                        .end();

                httpClient.close();

                log.debug("End HttpClientRequest to create Jira PJ");

            }

        } else {
            handler.handle(new Either.Right<String, JsonObject>(new JsonObject()
                    .putString("status", "OK")
                    .putObject("jsonPivotCompleted", jsonPivotCompleted)
            ));
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

        URI jira_get_infos_URI;
        final String urlGetTicketGeneralInfo = urlJiraFinal + jiraTicketId ;

        try {
            jira_get_infos_URI = new URI(urlGetTicketGeneralInfo);
        } catch (URISyntaxException e) {
            log.error("Invalid jira web service uri getJiraTicketContents", e);
            handler.handle(new Either.Left<String, JsonObject>("Invalid jira url : " + urlGetTicketGeneralInfo));
            return;
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
    private void updateJiraInformations (final String jiraTicketId,
                                         final JsonObject jsonPivot,
                                         final JsonObject jsonCurrentTicketInfos,
                                         final Handler<Either<String, JsonObject>> handler) {

        final String urlUpdateJiraTicket = urlJiraFinal + jiraTicketId;

        final JsonObject jsonJiraUpdateTicket = new JsonObject();
        jsonJiraUpdateTicket.putObject("fields", new JsonObject()
                .putString(JIRA_FIELD.getString("status_ent"), jsonPivot.getString(Supportpivot.STATUSENT_FIELD))
                .putString(JIRA_FIELD.getString("status_iws"), jsonPivot.getString(Supportpivot.STATUSIWS_FIELD))
                .putString(JIRA_FIELD.getString("resolution_ent"), jsonPivot.getString(Supportpivot.DATE_RESOENT_FIELD))
                .putString(JIRA_FIELD.getString("resolution_iws"), jsonPivot.getString(Supportpivot.DATE_RESOIWS_FIELD))
                .putString(("description"), jsonPivot.getString(Supportpivot.DESCRIPTION_FIELD))
                .putString("summary", jsonPivot.getString(Supportpivot.TITLE_FIELD)));

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
                        sendJiraComments(jiraTicketId, commentsLinkedList, jsonPivot, new Handler<Either<String, JsonObject>>() {
                            @Override
                            public void handle(Either<String, JsonObject> EitherCommentaires) {
                                if (EitherCommentaires.isRight()) {
                                    JsonObject jsonPivotCompleted = EitherCommentaires.right().getValue().getObject("jsonPivotCompleted");

                                    // Compare PJ and add only new ones
                                    JsonArray jsonPivotTicketPJ = jsonPivot.getArray("pj");
                                    JsonArray jsonCurrentTicketPJ = jsonCurrentTicketInfos.getObject("fields").getArray("attachment");
                                    JsonArray newPJ = comparePJ(jsonCurrentTicketPJ, jsonPivotTicketPJ);
                                    updateJiraPJ(jsonPivotCompleted, newPJ, jsonPivot, handler);

                                }
                                else {
                                    handler.handle(new Either.Left<String, JsonObject>(
                                            "Error, when creating PJ."));
                                }
                            }
                        });
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
                if (!(ot instanceof JsonObject)) continue;
                JsonObject ticketComment = (JsonObject) ot;
                String ticketCommentCreated = ticketComment.getString("created", "").trim();
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
     * Compare PJ.
     * Add every comment to ticket not already existing
     * @param ticketJiraPJ PJ of Jira ticket
     * @param issuePivotPJ PJ of pivot issue
     * @return comments that needs to be added in ticket
     */
    private JsonArray comparePJ(JsonArray ticketJiraPJ, JsonArray issuePivotPJ) {
        JsonArray pjToAdd = new JsonArray();

        for(Object oi : issuePivotPJ)  {
            if( !(oi instanceof JsonObject) ) continue;
            JsonObject pjIssuePivot = (JsonObject) oi;
            String issuePivotName;


            if(pjIssuePivot.containsField("nom")) {
                issuePivotName = pjIssuePivot.getString("nom", "");
            } else {
                log.error("Support : Invalid PJ : " + pjIssuePivot);
                continue;
            }

            boolean existing = false;

            for(Object ot : ticketJiraPJ) {
                if (!(ot instanceof JsonObject)) continue;
                JsonObject pjTicketJiraPJ = (JsonObject) ot;
                String ticketPJName = pjTicketJiraPJ.getString("filename", "").trim();

                if(issuePivotName.equals(ticketPJName)) {
                    existing = true;
                    break;
                }
            }
            if(!existing) {
                pjToAdd.addObject(pjIssuePivot);
            }
        }

        return pjToAdd;

    }

    /**
     * Send ticket informations to IWS when a Jira ticket has been modified
     * @param jiraTicketId Jira ticket ID received from REST JIRA when ticket updated
     */
    public void getTicketUpdatedToIWS (final HttpServerRequest request, final String jiraTicketId,
                                         final Handler<Either<String, JsonObject>> handler) {

        URI jira_get_infos_URI;
        final String urlGetTicketGeneralInfo = urlJiraFinal + jiraTicketId ;

        try {
            jira_get_infos_URI = new URI(urlGetTicketGeneralInfo);
        } catch (URISyntaxException e) {
            log.error("Invalid jira web service uri getTicketUpdatedToIWS", e);
            handler.handle(new Either.Left<String, JsonObject>("Invalid jira url : " + urlGetTicketGeneralInfo));
            return;
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
                            modifiedJiraJsonToIWS(jsonGetInfosTicket, handler);
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
    private void modifiedJiraJsonToIWS(final JsonObject jsonJiraTicketInfos,
                                           final Handler<Either<String, JsonObject>> handler) {


        if  (jsonJiraTicketInfos.getObject("fields").containsField(JIRA_FIELD.getString("id_iws"))
            && jsonJiraTicketInfos.getObject("fields").getString(JIRA_FIELD.getString("id_iws")) != null) {
                jsonJiraTicketToIWS(jsonJiraTicketInfos, handler);
            } else {
                handler.handle(new Either.Left<String, JsonObject>("Field " + JIRA_FIELD.getString("id_iws") + " does not exist."));
            }
    }



    /**
     * Create json pivot format information from JIRA to IWS -- by mail
     * Every data must be htmlEncoded because of encoding / mails incompatibility
     * @param jiraTicket JSON in JIRA format
     */
    private void jsonJiraTicketToIWS(JsonObject jiraTicket,
                                     final Handler<Either<String, JsonObject>> handler) {

        final JsonObject jsonPivot = new JsonObject();

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
            //Write only if the comment is public
            if (! comment.containsField("visibility")) {
                String commentFormated = serializeComment(comment);
                jsonCommentArray.addString(commentFormated);
            }
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

        String currentStatus = jiraTicket.getObject("fields").getObject("status").getString("name");


        String currentStatusToIWS;
        currentStatusToIWS = JIRA_MDP_STATUS.getArray("Default").get(0);
        for (String fieldName : JIRA_MDP_STATUS.getFieldNames()) {
            byte[] cstext = currentStatus.getBytes(UTF_8);
            String currentStatusEncoded = new String(cstext, ISO_8859_1);

            if (JIRA_MDP_STATUS.getArray(fieldName).contains(currentStatusEncoded)) {
               currentStatusToIWS = fieldName;
               break;
           }
        }

        byte[] cstext = currentStatusToIWS.getBytes(ISO_8859_1);
        String currentStatusReEncoded = new String(cstext, UTF_8);
        jsonPivot.putString(Supportpivot.STATUSJIRA_FIELD, currentStatusReEncoded);

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

        if  (jiraTicket.getObject("fields").getString(JIRA_FIELD.getString("response_technical")) != null) {
            jsonPivot.putString(Supportpivot.TECHNICAL_RESP_FIELD,
                    jiraTicket.getObject("fields").getString(JIRA_FIELD.getString("response_technical")));
        }

        jsonPivot.putString(Supportpivot.ATTRIBUTION_FIELD, Supportpivot.ATTRIBUTION_IWS);

        JsonArray attachments = jiraTicket.getObject("fields").getArray("attachment", new JsonArray());
        String jiraTicketID = jiraTicket.getString("key");
        final JsonArray allPJConverted = new JsonArray();
        final AtomicInteger nbAttachment = new AtomicInteger(attachments.size());
        final AtomicBoolean responseSent = new AtomicBoolean(false);

        for (Object a : attachments) {
            if (!(a instanceof JsonObject)) {
                nbAttachment.decrementAndGet();
                continue;
            }
            final JsonObject attachmentInfos = (JsonObject) a;
            getJiraPJ(jiraTicketID, attachmentInfos,
                    new Handler<Either<String, JsonObject>>() {
                @Override
                public void handle(Either<String, JsonObject> stringJsonObjectEither) {

                    if (stringJsonObjectEither.isRight()) {
                        String b64FilePJ = stringJsonObjectEither.right().getValue().getString("b64Attachment");
                        JsonObject currentPJ = new JsonObject();
                        currentPJ.putString(Supportpivot.ATTACHMENT_NAME_FIELD, attachmentInfos.getString("filename"));
                        currentPJ.putString(Supportpivot.ATTACHMENT_CONTENT_FIELD, b64FilePJ);
                        allPJConverted.addObject(currentPJ);
                    } else {
                        handler.handle(new Either.Left<String, JsonObject>(
                                "Error, the attachment received has a problem."));
                    }

                    if (nbAttachment.decrementAndGet() <= 0)
                    {
                        jsonPivot.putArray(Supportpivot.ATTACHMENT_FIELD, allPJConverted);
                        responseSent.set(true);
                        handler.handle(new Either.Right<String, JsonObject>(jsonPivot));
                    }
                }
            });
        }

        if (! responseSent.get() && nbAttachment.get() == 0) {
            handler.handle(new Either.Right<String, JsonObject>(jsonPivot));
        }

    }



    /**
     * Get Jira PJ via Jira API
     */
    private void getJiraPJ(final String jiraTicketId,
                           final JsonObject attachmentInfos,
                           final Handler<Either<String, JsonObject>> handler) {

        URI jira_get_attachment_URI;
        String attachmentLink = attachmentInfos.getString("content");
        final String urlGetTicketGeneralInfo = urlJiraFinal + jiraTicketId ;

        try {
            jira_get_attachment_URI = new URI(attachmentLink);
        } catch (URISyntaxException e) {
            log.error("Invalid jira web service uri getJiraPJ", e);
            handler.handle(new Either.Left<String, JsonObject>("Invalid jira url : " + urlGetTicketGeneralInfo));
            return;
        }

        final HttpClient httpClient = generateHttpClient(jira_get_attachment_URI);

        final HttpClientRequest httpClientRequestGetInfo = httpClient.get(attachmentLink, new Handler<HttpClientResponse>() {
            @Override
            public void handle(HttpClientResponse response) {
                if (response.statusCode() == 200) {
                    response.bodyHandler(new Handler<Buffer>() {
                        @Override
                        public void handle(Buffer bufferGetInfosTicket) {
                            String b64Attachment = Base64.encodeBytes(bufferGetInfosTicket.getBytes());
                            handler.handle(new Either.Right<String, JsonObject>(
                                    new JsonObject().putString("status", "OK")
                                                    .putString("b64Attachment", b64Attachment)));

                        }
                    });
                } else {
                    log.error("Error when calling URL : " + response.statusMessage());
                    handler.handle(new Either.Left<String, JsonObject>("Error when getting Jira attachment information"));
                }
            }
        });

        httpClientRequestGetInfo.putHeader("Authorization", "Basic " + Base64.encodeBytes(jiraAuthInfo.getBytes()));
        httpClient.close();
        httpClientRequestGetInfo.end();

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

    /**
     * Generate a Boundary for a Multipart HTTPRequest
     * return generated Boundary
     */
    private final static char[] MULTIPART_CHARS = "-_1234567890abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private String generateBoundary() {
        StringBuilder buffer = new StringBuilder();
        Random rand = new Random();
        int count = rand.nextInt(11) + 30; // a random size from 30 to 40
        for (int i = 0; i < count; i++) {
            buffer.append(MULTIPART_CHARS[rand.nextInt(MULTIPART_CHARS.length)]);
        }
        return buffer.toString();
    }

}