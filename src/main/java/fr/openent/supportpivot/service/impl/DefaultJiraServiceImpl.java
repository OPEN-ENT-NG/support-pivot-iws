package fr.openent.supportpivot.service.impl;

import fr.openent.supportpivot.Supportpivot;
import fr.openent.supportpivot.service.JiraService;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.text.Format;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Created by mercierq on 09/02/2018.
 * Default implementation for JiraService
 */
public class DefaultJiraServiceImpl implements JiraService {


    private final Logger log = LoggerFactory.getLogger(DefaultJiraServiceImpl.class);
    private final Vertx vertx;
    private final String JIRA_HOST;
    private final String jiraAuthInfo;
    private final String urlJiraFinal;
    private final String DEFAULT_COLLECTIVITY;
    private final String DEFAULT_TICKETTYPE;
    private final String DEFAULT_PRIORITY;

    private final JsonObject JIRA_FIELD;
    private final JsonObject JIRA_MDP_STATUS;
    private final String JIRA_MDP_STATUS_DEFAULT;


    private static Base64.Encoder encoder = Base64.getMimeEncoder().withoutPadding();
    private static Base64.Decoder decoder = Base64.getMimeDecoder();

    DefaultJiraServiceImpl(Vertx vertx, JsonObject config) {

        this.vertx = vertx;
        String jiraLogin = config.getString("jira-login");
        String jiraPassword = config.getString("jira-passwd");
        this.JIRA_HOST = config.getString("jira-host");
        String jiraUrl = config.getString("jira-url");
        int jiraPort = config.getInteger("jira-port");
        JIRA_FIELD = config.getJsonObject("jira-custom-fields");
        JIRA_MDP_STATUS = config.getJsonObject("status-mapping").getJsonObject("statutsJira");
        JIRA_MDP_STATUS_DEFAULT = config.getJsonObject("status-mapping").getString("statutsDefault");
        if(JIRA_FIELD == null) {
            log.fatal("Supportpivot : no jira-custom-fields in configuration");
        }

        jiraAuthInfo = jiraLogin + ":" + jiraPassword;
        urlJiraFinal = JIRA_HOST + ":" + jiraPort + jiraUrl;

        this.DEFAULT_COLLECTIVITY = config.getString("default-collectivity");
        this.DEFAULT_TICKETTYPE = config.getString("default-tickettype");
        this.DEFAULT_PRIORITY = config.getString("default-priority");

    }


    /**
     * Send pivot information from IWS -- to Jira
     * A ticket is created with the Jira API with all the json information received
     * @param jsonPivot JSON in pivot format
     */
    public void sendToJIRA(final JsonObject jsonPivot, final Handler<Either<String, JsonObject>> finalHandler) {

        //ID_IWS is mandatory
        if (!jsonPivot.containsKey(Supportpivot.IDIWS_FIELD)
                || jsonPivot.getString(Supportpivot.IDIWS_FIELD).isEmpty()) {

            finalHandler.handle(new Either.Left<>("2;Mandatory Field " + Supportpivot.IDIWS_FIELD));
            return;
        }


        String ticketType = DEFAULT_TICKETTYPE;
        if (jsonPivot.containsKey(Supportpivot.TICKETTYPE_FIELD)
            && ("Anomalie".equals(jsonPivot.getString(Supportpivot.TICKETTYPE_FIELD)) ||
                "Incident".equals(jsonPivot.getString(Supportpivot.TICKETTYPE_FIELD)) ||
                "Service".equals(jsonPivot.getString(Supportpivot.TICKETTYPE_FIELD)))) {
            ticketType = jsonPivot.getString(Supportpivot.TICKETTYPE_FIELD);
        }

        //TITLE = ID_IWS if not present
        if (!jsonPivot.containsKey(Supportpivot.TITLE_FIELD) || jsonPivot.getString(Supportpivot.TITLE_FIELD).isEmpty() ){
            jsonPivot.put(Supportpivot.TITLE_FIELD, jsonPivot.getString(Supportpivot.IDIWS_FIELD));
        }


        if (jsonPivot.containsKey(Supportpivot.IDJIRA_FIELD)
            && !jsonPivot.getString(Supportpivot.IDJIRA_FIELD).isEmpty()) {
            String jiraTicketId = jsonPivot.getString(Supportpivot.IDJIRA_FIELD);
            getJiraTicketContents(jsonPivot, jiraTicketId, finalHandler);
        } else {
            final JsonObject jsonJiraTicket = new JsonObject();

            String currentPriority = jsonPivot.getString(Supportpivot.PRIORITY_FIELD, DEFAULT_PRIORITY);
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
                    currentPriority = "Mineure";
                    break;
            }

            //TODO parametrer ce mapping
            String currentCollectivite = jsonPivot.getString(Supportpivot.COLLECTIVITY_FIELD);
            switch(currentCollectivite) {
                case "MDP":
                    currentCollectivite = "NGMDP";
                    break;
                default:
                    currentCollectivite = "NGMDP";
                    break;
            }

            jsonJiraTicket.put("fields", new JsonObject()
                    .put("project", new JsonObject()
                            .put("key", currentCollectivite))
                    .put("summary", jsonPivot.getString(Supportpivot.TITLE_FIELD))
                    .put("description", jsonPivot.getString(Supportpivot.DESCRIPTION_FIELD))
                    .put("issuetype", new JsonObject()
                            .put("name", ticketType))
                    .put("labels", jsonPivot.getJsonArray(Supportpivot.MODULES_FIELD))
                    .put(JIRA_FIELD.getString("id_ent"), jsonPivot.getString(Supportpivot.IDENT_FIELD))
                    .put(JIRA_FIELD.getString("id_iws"), jsonPivot.getString(Supportpivot.IDIWS_FIELD))
                    .put(JIRA_FIELD.getString("status_ent"), jsonPivot.getString(Supportpivot.STATUSENT_FIELD))
                    .put(JIRA_FIELD.getString("status_iws"), jsonPivot.getString(Supportpivot.STATUSIWS_FIELD))
                    .put(JIRA_FIELD.getString("creation"), jsonPivot.getString(Supportpivot.DATE_CREA_FIELD))
                    .put(JIRA_FIELD.getString("resolution_ent"), jsonPivot.getString(Supportpivot.DATE_RESOENT_FIELD))
                    .put(JIRA_FIELD.getString("resolution_iws"), jsonPivot.getString(Supportpivot.DATE_RESOIWS_FIELD))
                    .put(JIRA_FIELD.getString("creator"), jsonPivot.getString(Supportpivot.CREATOR_FIELD))
                    .put("priority", new JsonObject()
                            .put("name", currentPriority)));

            final JsonArray jsonJiraComments = jsonPivot.getJsonArray(Supportpivot.COMM_FIELD);

            final JsonArray jsonJiraPJ = jsonPivot.getJsonArray(Supportpivot.ATTACHMENT_FIELD);

            log.debug("Ticket content : " + jsonJiraTicket);

            // Create ticket via Jira API

            URI jira_URI;
            try {
                jira_URI = new URI(urlJiraFinal);
            } catch (URISyntaxException e) {
                log.error("Invalid jira web service uri sendToJIRA", e);
                finalHandler.handle(new Either.Left<>("Invalid jira url"));
                return;
            }

            final HttpClient httpClient = generateHttpClient(jira_URI);

            final HttpClientRequest httpClientRequest = httpClient.post(urlJiraFinal,
                    response -> {
                        //TODO gérér les status 302 ici
                        // HTTP Status Code 201: The request has been fulfilled and has resulted in one or more new resources being created.
                        if (response.statusCode() == 201) {

                            updateComments(response, jsonPivot, jsonJiraComments,
                                    EitherCommentaires -> {
                                        if (EitherCommentaires.isRight()) {
                                            JsonObject jsonPivotCompleted = EitherCommentaires.right().getValue().getJsonObject("jsonPivotCompleted");
                                            updateJiraPJ(jsonPivotCompleted, jsonJiraPJ, jsonPivot, finalHandler);
                                        } else {
                                            finalHandler.handle(new Either.Left<>(
                                                    "999;Error, when creating comments."));
                                        }
                                    });
                        } else {
                            log.error("Error when calling URL : " + response.statusCode() + response.statusMessage() + ". Error when creating Jira ticket.");
                            finalHandler.handle(new Either.Left<>("999;Error when creating Jira ticket"));
                        }
                    });


            httpClientRequest.putHeader(HttpHeaders.HOST, JIRA_HOST)
                    .putHeader("Authorization", "Basic " + encoder.encodeToString(jiraAuthInfo.getBytes()))
                    .putHeader("Content-Type", "application/json")
                    .setChunked(true)
                    .write(jsonJiraTicket.encode());

            httpClient.close();
            httpClientRequest.end();

        }
    }

    private void updateComments(final HttpClientResponse response, final JsonObject jsonPivot,
                                 final JsonArray jsonJiraComments,
                                 final Handler<Either<String, JsonObject>> handler) {



            response.bodyHandler(buffer -> {

                JsonObject infoNewJiraTicket = new JsonObject(buffer.toString());
                String idNewJiraTicket = infoNewJiraTicket.getString("key");

                log.debug("JIRA ticket Informations : " + infoNewJiraTicket);
                log.debug("JIRA ticket ID created : " + idNewJiraTicket);

                LinkedList<String> commentsLinkedList = new LinkedList<>();

                jsonPivot.put("id_jira", idNewJiraTicket);
                jsonPivot.put("statut_jira", "Nouveau");

                if ( jsonJiraComments != null ) {
                    for( Object comment : jsonJiraComments ) {
                        commentsLinkedList.add(comment.toString());
                    }
                    sendJiraComments(idNewJiraTicket, commentsLinkedList, jsonPivot, handler);
                }
                else {
                    handler.handle(new Either.Right<>(new JsonObject()
                            .put("status", "OK")
                            .put("jsonPivotCompleted", jsonPivot)
                    ));
                }

            });


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
                handler.handle(new Either.Left<>("Invalid jira url : " + urlNewTicket));
            }

            if (jira_add_comment_URI != null) {
                final HttpClient httpClient = generateHttpClient(jira_add_comment_URI);

                final HttpClientRequest httpClientRequest = httpClient.post(urlNewTicket , response -> {

                    // If ticket well created, then HTTP Status Code 201: The request has been fulfilled and has resulted in one or more new resources being created.
                    if (response.statusCode() != 201) {
                        handler.handle(new Either.Left<>("999;Error when add Jira comment : " + commentsLinkedList.getFirst().toString() + " : " + response.statusCode() + " " + response.statusMessage()));
                        log.error("Error when add Jira comment" + idJira + commentsLinkedList.getFirst().toString());
                        return;
                    }
                    //Recursive call
                    commentsLinkedList.removeFirst();
                    sendJiraComments(idJira, commentsLinkedList, jsonPivot, handler);
                });
                httpClientRequest.putHeader(HttpHeaders.HOST, JIRA_HOST)
                        .putHeader("Authorization", "Basic " + encoder.encodeToString(jiraAuthInfo.getBytes()))
                        .putHeader("Content-Type", "application/json")
                        .setChunked(true);

                final JsonObject jsonCommTicket = new JsonObject();
                jsonCommTicket.put("body", commentsLinkedList.getFirst().toString());
                httpClientRequest.write(jsonCommTicket.encode());

                httpClient.close();
                httpClientRequest.end();

            }


        } else {
            handler.handle(new Either.Right<>(new JsonObject()
                    .put("status", "OK")
                    .put("jsonPivotCompleted", jsonPivot)
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
            handler.handle(new Either.Right<>(new JsonObject()
                    .put("status", "OK")
                    .put("jsonPivotCompleted", jsonPivot)
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
                handler.handle(new Either.Left<>("Invalid jira url : " + urlNewTicket));
            }

            if (jira_add_pj_URI != null) {
                final HttpClient httpClient = generateHttpClient(jira_add_pj_URI);

                final HttpClientRequest httpClientRequest = httpClient.post(urlNewTicket , response -> {

                    if (response.statusCode() != 200) {
                        handler.handle(new Either.Left<>("999;Error when add Jira attachment : " + pjLinkedList.getFirst().getString("nom") + " : " + response.statusCode() + " " + response.statusMessage()));
                        log.error("Error when add Jira comment" + idJira +  pjLinkedList.getFirst().getString("nom"));
                        return;
                    }
                    //Recursive call
                    pjLinkedList.removeFirst();
                    sendJiraPJ(idJira, pjLinkedList, jsonPivotCompleted, handler);

                });

                String currentBoundary = generateBoundary();

                httpClientRequest.putHeader(HttpHeaders.HOST, JIRA_HOST)
                        .putHeader("X-Atlassian-Token", "no-check")
                        .putHeader("Authorization", "Basic " + encoder.encodeToString(jiraAuthInfo.getBytes()))
                        .putHeader("Content-Type", "multipart/form-data; boundary=" + currentBoundary);

                String debRequest = "--" + currentBoundary + "\r\n" +
                        "Content-Type: application/octet-stream\r\n" +
                        "Content-Disposition: form-data; name=\"file\"; filename=\"" +
                        pjLinkedList.getFirst().getString("nom")
                        + "\"\r\n\r\n";

                String finRequest = "\r\n--" + currentBoundary + "--";

                byte[] debBytes = debRequest.getBytes();
                byte[] pjBytes = decoder.decode(pjLinkedList.getFirst().getString("contenu"));
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
                        .write(Buffer.buffer(all))
                        .end();

                httpClient.close();

                log.debug("End HttpClientRequest to create Jira PJ");

            }

        } else {
            handler.handle(new Either.Right<>(new JsonObject()
                    .put("status", "OK")
                    .put("jsonPivotCompleted", jsonPivotCompleted)
            ));
        }

    }


    /**
     * Generate HTTP client
     * @param uri uri
     * @return Http client
     */
    private HttpClient generateHttpClient(URI uri) {
        HttpClientOptions httpClientOptions = new HttpClientOptions()
                .setDefaultHost(uri.getHost())
                .setDefaultPort((uri.getPort() > 0) ? uri.getPort() : ("https".equals(uri.getScheme()) ? 443 : 80))
                .setVerifyHost(false)
                .setTrustAll(true)
                .setSsl("https".equals(uri.getScheme()))
                .setKeepAlive(false);
        return vertx.createHttpClient(httpClientOptions);
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
            handler.handle(new Either.Left<>("999;Invalid jira url : " + urlGetTicketGeneralInfo));
            return;
        }

        final HttpClient httpClient = generateHttpClient(jira_get_infos_URI);

        final HttpClientRequest httpClientRequestGetInfo = httpClient.get(urlGetTicketGeneralInfo, response -> processJiraInfo(response, jsonPivot, jiraTicketId, handler));

        httpClientRequestGetInfo.putHeader("Authorization", "Basic " + encoder.encodeToString(jiraAuthInfo.getBytes()));
        httpClient.close();
        httpClientRequestGetInfo.end();

    }

    private void processJiraInfo(HttpClientResponse response, final JsonObject jsonPivot, final String jiraTicketId,
                                 final Handler<Either<String, JsonObject>> handler) {
        switch (response.statusCode()){
            case (200) :
                response.bodyHandler(bufferGetInfosTicket -> {
                    JsonObject jsonGetInfosTicket = new JsonObject(bufferGetInfosTicket.toString());
                    updateJiraInformations(jiraTicketId, jsonPivot, jsonGetInfosTicket, handler);
                });
                break;
            case (404) :
                handler.handle(new Either.Left<>("101;Unknown JIRA Ticket " + jiraTicketId));
                break;
            default :
                log.error("Error when calling URL : " + response.statusMessage());
                handler.handle(new Either.Left<>("999;Error when getting Jira ticket information"));
        }
    }


    /**
     * Update ticket information via Jira API
     */
    private void updateJiraInformations (final String jiraTicketId,
                                         final JsonObject jsonPivot,
                                         final JsonObject jsonCurrentTicketInfos,
                                         final Handler<Either<String, JsonObject>> handler) {

        //Is JIRA ticket had been created by IWS ?
        String jiraTicketIdIWS =jsonCurrentTicketInfos.getJsonObject("fields").getString(JIRA_FIELD.getString("id_iws"));
        if(jiraTicketIdIWS==null){
            handler.handle(new Either.Left<>("102;Not an IWS ticket."));
            return;
        }

        //Is JIRA ticket had been created by same IWS issue ?
        String jsonPivotIdIWS = jsonPivot.getString(Supportpivot.IDIWS_FIELD);
        if(!jiraTicketIdIWS.equals(jsonPivotIdIWS)){
            handler.handle(new Either.Left<>("102;JIRA Ticket " + jiraTicketId + " already link with an another IWS issue"));
            return;
        }

        final String urlUpdateJiraTicket = urlJiraFinal + jiraTicketId;

        final JsonObject jsonJiraUpdateTicket = new JsonObject();
        jsonJiraUpdateTicket.put("fields", new JsonObject()
                .put(JIRA_FIELD.getString("status_ent"), jsonPivot.getString(Supportpivot.STATUSENT_FIELD))
                .put(JIRA_FIELD.getString("status_iws"), jsonPivot.getString(Supportpivot.STATUSIWS_FIELD))
                .put(JIRA_FIELD.getString("resolution_ent"), jsonPivot.getString(Supportpivot.DATE_RESOENT_FIELD))
                .put(JIRA_FIELD.getString("resolution_iws"), jsonPivot.getString(Supportpivot.DATE_RESOIWS_FIELD))
                .put(("description"), jsonPivot.getString(Supportpivot.DESCRIPTION_FIELD))
                .put("summary", jsonPivot.getString(Supportpivot.TITLE_FIELD))
                .put(JIRA_FIELD.getString("creator"), jsonPivot.getString(Supportpivot.CREATOR_FIELD)));

        URI jira_update_infos_URI;
        try {
            jira_update_infos_URI = new URI(urlUpdateJiraTicket);
        } catch (URISyntaxException e) {
            log.error("Invalid jira web service uri", e);
            handler.handle(new Either.Left<>("Invalid jira url"));
            return;
        }

        final HttpClient httpClient = generateHttpClient(jira_update_infos_URI);


        final HttpClientRequest httpClientRequest = httpClient.put(urlUpdateJiraTicket , response -> {
            if (response.statusCode() == 204) {

                // Compare comments and add only new ones
                JsonArray jsonPivotTicketComments = jsonPivot.getJsonArray("commentaires", new JsonArray());
                JsonArray jsonCurrentTicketComments = jsonCurrentTicketInfos.getJsonObject("fields").getJsonObject("comment").getJsonArray("comments");
                JsonArray newComments = compareComments(jsonCurrentTicketComments, jsonPivotTicketComments);

                LinkedList<String> commentsLinkedList = new LinkedList<>();

                if ( newComments != null ) {
                    for( Object comment : newComments ) {
                        commentsLinkedList.add(comment.toString());
                    }
                    sendJiraComments(jiraTicketId, commentsLinkedList, jsonPivot, EitherCommentaires -> {
                        if (EitherCommentaires.isRight()) {
                            JsonObject jsonPivotCompleted = EitherCommentaires.right().getValue().getJsonObject("jsonPivotCompleted");

                            // Compare PJ and add only new ones
                            JsonArray jsonPivotTicketPJ = jsonPivot.getJsonArray("pj", new JsonArray());
                            JsonArray jsonCurrentTicketPJ = jsonCurrentTicketInfos.getJsonObject("fields").getJsonArray("attachment");
                            JsonArray newPJ = comparePJ(jsonCurrentTicketPJ, jsonPivotTicketPJ);
                            updateJiraPJ(jsonPivotCompleted, newPJ, jsonPivot, handler);

                        } else {
                            handler.handle(new Either.Left<>(
                                    "Error, when creating PJ."));
                        }
                    });
                }
                else {
                    handler.handle(new Either.Right<>(new JsonObject().put("status", "OK")));
                }
            }
            else {
                log.error("Error when calling URL : " + response.statusMessage());
                handler.handle(new Either.Left<>("Error when update Jira ticket information"));
            }
        });

        httpClientRequest.putHeader(HttpHeaders.HOST, JIRA_HOST)
                .putHeader("Authorization", "Basic " + encoder.encodeToString(jiraAuthInfo.getBytes()))
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
            jsonComment.put("id", elements[0].trim());
            jsonComment.put("owner", elements[1].trim());
            jsonComment.put("created", elements[2].trim());
            StringBuilder content = new StringBuilder();
            for(int i = 3; i<elements.length ; i++) {
                content.append(elements[i]);
                content.append("|");
            }
            content.deleteCharAt(content.length() - 1);
            jsonComment.put("content", content.toString());
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
        JsonArray commentsToAdd = new fr.wseduc.webutils.collections.JsonArray();
        for(Object oi : issuePivotComments)  {
            if( !(oi instanceof String) ) continue;
            String rawComment = (String)oi;
            JsonObject issueComment = unserializeComment(rawComment);
            String issueCommentId;

            if(issueComment != null && issueComment.containsKey("id")) {
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
                commentsToAdd.add(rawComment);
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
        JsonArray pjToAdd = new fr.wseduc.webutils.collections.JsonArray();

        for(Object oi : issuePivotPJ)  {
            if( !(oi instanceof JsonObject) ) continue;
            JsonObject pjIssuePivot = (JsonObject) oi;
            String issuePivotName;


            if(pjIssuePivot.containsKey("nom")) {
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
                pjToAdd.add(pjIssuePivot);
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
            handler.handle(new Either.Left<>("Invalid jira url : " + urlGetTicketGeneralInfo));
            return;
        }

        final HttpClient httpClient = generateHttpClient(jira_get_infos_URI);

        final HttpClientRequest httpClientRequestGetInfo = httpClient.get(urlGetTicketGeneralInfo, response -> {

            if (response.statusCode() == 200) {
                response.bodyHandler(bufferGetInfosTicket -> {
                    JsonObject jsonGetInfosTicket = new JsonObject(bufferGetInfosTicket.toString());
                    modifiedJiraJsonToIWS(jsonGetInfosTicket, handler);
                });
            } else {
                log.error("Error when calling URL : " + response.statusMessage());
                handler.handle(new Either.Left<>("Error when getting Jira ticket information"));
            }
        });

        httpClientRequestGetInfo.putHeader("Authorization", "Basic " + encoder.encodeToString(jiraAuthInfo.getBytes()));
        httpClient.close();
        httpClientRequestGetInfo.end();

    }


    /**
     * Modified Jira JSON to prepare to send the email to IWS
     */
    private void modifiedJiraJsonToIWS(final JsonObject jsonJiraTicketInfos,
                                           final Handler<Either<String, JsonObject>> handler) {


        if  (jsonJiraTicketInfos.getJsonObject("fields").containsKey(JIRA_FIELD.getString("id_iws"))
            && jsonJiraTicketInfos.getJsonObject("fields").getString(JIRA_FIELD.getString("id_iws")) != null) {
                jsonJiraTicketToIWS(jsonJiraTicketInfos, handler);
            } else {
                handler.handle(new Either.Left<>("Field " + JIRA_FIELD.getString("id_iws") + " does not exist."));
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

        String currentCollectivite = jiraTicket.getJsonObject("fields").getJsonObject("project").getString("name");
        switch(currentCollectivite) {
            case "NGMDP":
                currentCollectivite = DEFAULT_COLLECTIVITY;
                break;
            default:
                currentCollectivite = DEFAULT_COLLECTIVITY;
                break;
        }

        jsonPivot.put(Supportpivot.COLLECTIVITY_FIELD, currentCollectivite);

        jsonPivot.put(Supportpivot.ACADEMY_FIELD, "PARIS");


        if  (jiraTicket.getJsonObject("fields").getString(JIRA_FIELD.getString("creator")) != null) {
            jsonPivot.put(Supportpivot.CREATOR_FIELD,
                    jiraTicket.getJsonObject("fields").getString(stringEncode(JIRA_FIELD.getString("creator"))));
        } else {
            jsonPivot.put(Supportpivot.CREATOR_FIELD, "");
        }

        jsonPivot.put(Supportpivot.TICKETTYPE_FIELD, jiraTicket.getJsonObject("fields")
                        .getJsonObject("issuetype").getString("name"))
                .put(Supportpivot.TITLE_FIELD,
                        jiraTicket.getJsonObject("fields").getString("summary"));

        if  (jiraTicket.getJsonObject("fields").getString("description") != null) {
            jsonPivot.put(Supportpivot.DESCRIPTION_FIELD,
                    jiraTicket.getJsonObject("fields").getString("description"));
        } else {
            jsonPivot.put(Supportpivot.DESCRIPTION_FIELD, "");
        }


        String currentPriority = jiraTicket.getJsonObject("fields").getJsonObject("priority").getString("name");
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

        jsonPivot.put(Supportpivot.PRIORITY_FIELD, currentPriority);

        jsonPivot.put(Supportpivot.MODULES_FIELD,
                jiraTicket.getJsonObject("fields").getJsonArray("labels")).put(Supportpivot.IDJIRA_FIELD, jiraTicket.getString("key"));

        if  (jiraTicket.getJsonObject("fields").getString(JIRA_FIELD.getString("id_ent")) != null) {
            jsonPivot.put(Supportpivot.IDENT_FIELD,
                    jiraTicket.getJsonObject("fields").getString(JIRA_FIELD.getString("id_ent")));
        } else {
            jsonPivot.put(Supportpivot.IDENT_FIELD, "");
        }

        if  (jiraTicket.getJsonObject("fields").getString(JIRA_FIELD.getString("id_iws")) != null) {
            jsonPivot.put(Supportpivot.IDIWS_FIELD,
                    jiraTicket.getJsonObject("fields").getString(JIRA_FIELD.getString("id_iws")));
        }

        JsonArray comm = jiraTicket.getJsonObject("fields").getJsonObject("comment")
                .getJsonArray("comments", new fr.wseduc.webutils.collections.JsonArray());
        JsonArray jsonCommentArray = new fr.wseduc.webutils.collections.JsonArray();
        for(int i=0 ; i<comm.size();i++){
            JsonObject comment = comm.getJsonObject(i);
            //Write only if the comment is public
            if (! comment.containsKey("visibility")) {
                String commentFormated = serializeComment(comment);
                jsonCommentArray.add(commentFormated);
            }
        }
        jsonPivot.put(Supportpivot.COMM_FIELD, jsonCommentArray);

        if  (jiraTicket.getJsonObject("fields").getString(JIRA_FIELD.getString("status_ent")) != null) {
            jsonPivot.put(Supportpivot.STATUSENT_FIELD,
                    jiraTicket.getJsonObject("fields").getString(JIRA_FIELD.getString("status_ent")));
        }
        if  (jiraTicket.getJsonObject("fields").getString(JIRA_FIELD.getString("status_iws")) != null) {
            jsonPivot.put(Supportpivot.STATUSIWS_FIELD,
                    jiraTicket.getJsonObject("fields").getString(JIRA_FIELD.getString("status_iws")));
        }

        String currentStatus = jiraTicket.getJsonObject("fields").getJsonObject("status").getString("name");

        String currentStatusToIWS;
        currentStatusToIWS = JIRA_MDP_STATUS_DEFAULT;
        for (String fieldName : JIRA_MDP_STATUS.fieldNames()) {
            if (JIRA_MDP_STATUS.getJsonArray(fieldName).contains(currentStatus)) {
               currentStatusToIWS = fieldName;
               break;
           }
        }

        jsonPivot.put(Supportpivot.STATUSJIRA_FIELD, currentStatusToIWS);

        if  (jiraTicket.getJsonObject("fields").getString(JIRA_FIELD.getString("creation")) != null) {
            jsonPivot.put(Supportpivot.DATE_CREA_FIELD,
                    jiraTicket.getJsonObject("fields").getString(JIRA_FIELD.getString("creation")));
        }

        if  (jiraTicket.getJsonObject("fields").getString(JIRA_FIELD.getString("resolution_iws")) != null) {
            jsonPivot.put(Supportpivot.DATE_RESOIWS_FIELD,
                    jiraTicket.getJsonObject("fields").getString(JIRA_FIELD.getString("resolution_iws")));
        }

        if  (jiraTicket.getJsonObject("fields").getString(JIRA_FIELD.getString("resolution_ent")) != null) {
            jsonPivot.put(Supportpivot.DATE_RESOENT_FIELD,
                    jiraTicket.getJsonObject("fields").getString(JIRA_FIELD.getString("resolution_ent")));
        }

        if  (jiraTicket.getJsonObject("fields").getString("resolutiondate") != null) {
            String dateFormated = getDateFormatted(jiraTicket.getJsonObject("fields").getString("resolutiondate"), false);
            jsonPivot.put(Supportpivot.DATE_RESOJIRA_FIELD, dateFormated);
        }

        if  (jiraTicket.getJsonObject("fields").getString(JIRA_FIELD.getString("response_technical")) != null) {
            jsonPivot.put(Supportpivot.TECHNICAL_RESP_FIELD,
                    jiraTicket.getJsonObject("fields").getString(JIRA_FIELD.getString("response_technical")));
        }

        jsonPivot.put(Supportpivot.ATTRIBUTION_FIELD, Supportpivot.ATTRIBUTION_IWS);

        JsonArray attachments = jiraTicket.getJsonObject("fields").getJsonArray("attachment", new fr.wseduc.webutils.collections.JsonArray());
        String jiraTicketID = jiraTicket.getString("key");
        final JsonArray allPJConverted = new fr.wseduc.webutils.collections.JsonArray();
        final AtomicInteger nbAttachment = new AtomicInteger(attachments.size());
        final AtomicBoolean responseSent = new AtomicBoolean(false);

        for (Object a : attachments) {
            if (!(a instanceof JsonObject)) {
                nbAttachment.decrementAndGet();
                continue;
            }
            final JsonObject attachmentInfos = (JsonObject) a;
            getJiraPJ(jiraTicketID, attachmentInfos, stringJsonObjectEither -> {

                    if (stringJsonObjectEither.isRight()) {
                        String b64FilePJ = stringJsonObjectEither.right().getValue().getString("b64Attachment");
                        JsonObject currentPJ = new JsonObject();
                        currentPJ.put(Supportpivot.ATTACHMENT_NAME_FIELD, attachmentInfos.getString("filename"));
                        currentPJ.put(Supportpivot.ATTACHMENT_CONTENT_FIELD, b64FilePJ);
                        allPJConverted.add(currentPJ);
                    } else {
                        handler.handle(new Either.Left<>(
                                "Error, the attachment received has a problem."));
                    }

                    if (nbAttachment.decrementAndGet() <= 0)
                    {
                        jsonPivot.put(Supportpivot.ATTACHMENT_FIELD, allPJConverted);
                        responseSent.set(true);
                        handler.handle(new Either.Right<>(jsonPivot));
                    }
                }
            );
        }

        if (! responseSent.get() && nbAttachment.get() == 0) {
            handler.handle(new Either.Right<>(jsonPivot));
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
            handler.handle(new Either.Left<>("Invalid jira url : " + urlGetTicketGeneralInfo));
            return;
        }

        final HttpClient httpClient = generateHttpClient(jira_get_attachment_URI);

        final HttpClientRequest httpClientRequestGetInfo = httpClient.get(attachmentLink, response -> {
            if (response.statusCode() == 200) {
                response.bodyHandler(bufferGetInfosTicket -> {
                    String b64Attachment = encoder.encodeToString(bufferGetInfosTicket.getBytes());
                    handler.handle(new Either.Right<>(
                            new JsonObject().put("status", "OK")
                                            .put("b64Attachment", b64Attachment)));

                });
            } else {
                log.error("Error when calling URL : " + response.statusMessage());
                handler.handle(new Either.Left<>("Error when getting Jira attachment information"));
            }
        });

        httpClientRequestGetInfo.putHeader("Authorization", "Basic " + encoder.encodeToString(jiraAuthInfo.getBytes()));
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
                + " | " + comment.getJsonObject("author").getString("displayName")
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