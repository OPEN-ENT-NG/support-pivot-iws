/*
 *
 * Copyright (c) Mairie de Paris, CGI, 2016.
 * This file is part of OPEN ENT NG. OPEN ENT NG is a versatile ENT Project based on the JVM and ENT Core Project.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with OPEN ENT NG is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of OPEN ENT NG, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package fr.openent.supportpivot.services;

import fr.openent.supportpivot.managers.ConfigManager;
import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.ProxyOptions;

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

import static fr.openent.supportpivot.constants.PivotConstants.*;
import static fr.openent.supportpivot.model.ticket.PivotTicket.*;

/**
 * Created by mercierq on 09/02/2018.
 * Default implementation for JiraService
 */
public class JiraServiceImpl implements JiraService {

    private final Logger LOGGER = LoggerFactory.getLogger(JiraServiceImpl.class);
    private final Vertx vertx;
    private final URI JIRA_HOST;
    private final String JIRA_AUTH_INFO;
    private final URI JIRA_REST_API_URI;
    private final String JIRA_PROJECT_NAME;
    private final String ACADEMY_NAME;
    private final String DEFAULT_JIRA_TICKETTYPE;
    private final String DEFAULT_PRIORITY;
    private final JsonArray JIRA_ALLOWED_PRIORITY;
    private final JsonArray JIRA_ALLOWED_TICKETTYPE;
    private final JsonObject JIRA_FIELD;
    private final JsonObject JIRA_STATUS_MAPPING;
    private final String JIRA_STATUS_DEFAULT;

    private HttpClient httpClient;
    private static Base64.Encoder encoder = Base64.getMimeEncoder().withoutPadding();
    private static Base64.Decoder decoder = Base64.getMimeDecoder();

    private static final int HTTP_STATUS_200_OK = 200;
    private static final int HTTP_STATUS_204_NO_CONTENT = 204;
    private static final int HTTP_STATUS_404_NOT_FOUND = 404;
    private static final int HTTP_STATUS_201_CREATED = 201;

    public JiraServiceImpl(Vertx vertx, JsonObject config) {

        this.vertx = vertx;
        String jiraLogin = config.getString("jira-login");
        String jiraPassword = config.getString("jira-passwd");
        this.JIRA_AUTH_INFO = jiraLogin + ":" + jiraPassword;

        URI jira_host_uri = null;
        try {
            jira_host_uri = new URI(config.getString("jira-host"));
        } catch (URISyntaxException e) {
            LOGGER.error("Bad parameter ent-core.json#jira-host ", e);
        }
        this.JIRA_HOST = jira_host_uri;
        assert JIRA_HOST != null;

        URI jira_rest_uri = null;
        try {
            jira_rest_uri = new URI(config.getString("jira-url"));
        } catch (URISyntaxException e) {
            LOGGER.error("Bad parameter ent-core.json#jira-url ", e);
            //TODO Break module starting
        }
        this.JIRA_REST_API_URI = jira_rest_uri;
        assert JIRA_REST_API_URI != null;

        this.JIRA_PROJECT_NAME = config.getString("jira-project-key");
        this.ACADEMY_NAME = config.getString("academy");
        JIRA_FIELD = config.getJsonObject("jira-custom-fields");

        if(JIRA_FIELD.containsKey("id_external")) {
            //Retro-compatibility external fields are historical labeled iws
            JIRA_FIELD.put("id_iws", JIRA_FIELD.getString("id_external"));
            JIRA_FIELD.put("status_iws", JIRA_FIELD.getString("status_external"));
            JIRA_FIELD.put("resolution_iws", JIRA_FIELD.getString("resolution_external"));
        }
        JIRA_STATUS_MAPPING = config.getJsonObject("jira-status-mapping").getJsonObject("statutsJira");
        JIRA_STATUS_DEFAULT = config.getJsonObject("jira-status-mapping").getString("statutsDefault");
        JIRA_ALLOWED_TICKETTYPE = config.getJsonArray("jira-allowed-tickettype");
        this.DEFAULT_JIRA_TICKETTYPE = config.getString("default-tickettype");
        this.DEFAULT_PRIORITY = config.getString("default-priority");
        this.JIRA_ALLOWED_PRIORITY = config.getJsonArray("jira-allowed-priority");

        this.httpClient = generateHttpClient(JIRA_HOST);
    }

    /**
     * Generate HTTP client
     *
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
                .setKeepAlive(true);

        if (ConfigManager.getInstance().getProxyHost() != null) {
            ProxyOptions proxy = new ProxyOptions();
            proxy.setHost(ConfigManager.getInstance().getProxyHost());
            proxy.setPort(ConfigManager.getInstance().getProxyPort());
            httpClientOptions.setProxyOptions(proxy);
        }

        return vertx.createHttpClient(httpClientOptions);
    }

    private void terminateRequest(HttpClientRequest httpClientRequest) {
        httpClientRequest.putHeader("Authorization", "Basic " + encoder.encodeToString(JIRA_AUTH_INFO.getBytes()))
                .setFollowRedirects(true);
        if (!httpClientRequest.headers().contains("Content-Type")) {
            httpClientRequest.putHeader("Content-Type", "application/json");
        }

        httpClientRequest.exceptionHandler(exception->{
            LOGGER.error("Error when update Jira ticket",exception );
        });
        httpClientRequest.end();
    }

    /**
     * Send pivot information from IWS -- to Jira<
     * A ticket is created with the Jira API with all the json information received
     *
     * @param jsonPivotIn JSON in pivot format
     * @param handler     return JsonPivot from Jira or error message
     */
    public void sendToJIRA(final JsonObject jsonPivotIn, final Handler<Either<String, JsonObject>> handler) {

        //ID_IWS is mandatory
        if (!jsonPivotIn.containsKey(IDIWS_FIELD)
                || jsonPivotIn.getString(IDIWS_FIELD).isEmpty()) {

            handler.handle(new Either.Left<>("2;Mandatory Field " + IDIWS_FIELD));
            return;
        }

        //TITLE  is mandatory : TITLE = ID_IWS if not present
        if (!jsonPivotIn.containsKey(TITLE_FIELD) || jsonPivotIn.getString(TITLE_FIELD).isEmpty()) {
            jsonPivotIn.put(TITLE_FIELD, jsonPivotIn.getString(IDIWS_FIELD));
        }

        if (jsonPivotIn.containsKey(IDJIRA_FIELD)
                && !jsonPivotIn.getString(IDJIRA_FIELD).isEmpty()) {
            String jiraTicketId = jsonPivotIn.getString(IDJIRA_FIELD);
            updateJiraTicket(jsonPivotIn, jiraTicketId, handler);
        } else {
            try {
                this.createJiraTicket(jsonPivotIn, handler);
            } catch (Error e) {
                handler.handle(new Either.Left<>("2;An error occurred  while creating ticket for id_iws " + IDIWS_FIELD + ": " + e.getMessage()));
            }
        }
    }

    private void createJiraTicket(JsonObject jsonPivotIn, Handler<Either<String, JsonObject>> handler) {
        final JsonObject jsonJiraTicket = new JsonObject();

        //Ticket Type
        String ticketType = DEFAULT_JIRA_TICKETTYPE;
        if (jsonPivotIn.containsKey(TICKETTYPE_FIELD)
                && JIRA_ALLOWED_TICKETTYPE.contains(jsonPivotIn.getString(TICKETTYPE_FIELD))) {
            ticketType = jsonPivotIn.getString(TICKETTYPE_FIELD);
        }

        // priority PIVOT -> JIRA
        String jsonPriority = jsonPivotIn.getString(PRIORITY_FIELD);
        if (!PIVOT_PRIORITY_LEVEL.contains(jsonPriority)) {
            jsonPriority = DEFAULT_PRIORITY;
        }
        String currentPriority = JIRA_ALLOWED_PRIORITY.getString(PIVOT_PRIORITY_LEVEL.indexOf(jsonPriority));

        jsonJiraTicket.put("fields", new JsonObject()
                .put("project", new JsonObject()
                        .put("key", JIRA_PROJECT_NAME))
                .put("summary", jsonPivotIn.getString(TITLE_FIELD))
                .put("description", jsonPivotIn.getString(DESCRIPTION_FIELD))
                .put("issuetype", new JsonObject()
                        .put("name", ticketType))
                .put("labels", jsonPivotIn.getJsonArray(MODULES_FIELD))
                .put(JIRA_FIELD.getString("id_ent"), jsonPivotIn.getString(ID_FIELD))
                .put(JIRA_FIELD.getString("id_iws"), jsonPivotIn.getString(IDIWS_FIELD))
                .put(JIRA_FIELD.getString("status_ent"), jsonPivotIn.getString(STATUSENT_FIELD))
                .put(JIRA_FIELD.getString("status_iws"), jsonPivotIn.getString(STATUSIWS_FIELD))
                .put(JIRA_FIELD.getString("creation"), jsonPivotIn.getString(DATE_CREA_FIELD))
                .put(JIRA_FIELD.getString("resolution_ent"), jsonPivotIn.getString(DATE_RESO_FIELD))
                .put(JIRA_FIELD.getString("resolution_iws"), jsonPivotIn.getString(DATE_RESOIWS_FIELD))
                .put(JIRA_FIELD.getString("creator"), jsonPivotIn.getString(CREATOR_FIELD))
                .put("priority", new JsonObject()
                        .put("name", currentPriority)));

        try {
            // Create ticket via Jira API
            final HttpClientRequest createTicketRequest = httpClient.post(JIRA_REST_API_URI.toString(),
                    response -> {
                        // HTTP Status Code 201: The request has been fulfilled and has resulted in one or more new resources being created.
                        if (response.statusCode() == HTTP_STATUS_201_CREATED) {
                            final JsonArray jsonJiraComments = jsonPivotIn.getJsonArray(COMM_FIELD);

                            updateComments(response, jsonPivotIn, jsonJiraComments,
                                    EitherCommentaires -> {
                                        if (EitherCommentaires.isRight()) {
                                            JsonObject jsonPivotCompleted = EitherCommentaires.right().getValue().getJsonObject("jsonPivotCompleted");
                                            final JsonArray jsonJiraPJ = jsonPivotIn.getJsonArray(ATTACHMENT_FIELD);
                                            updateJiraPJ(jsonPivotCompleted, jsonJiraPJ, jsonPivotIn, handler);
                                        } else {
                                            handler.handle(new Either.Left<>(
                                                    "999;Error, when creating comments."));
                                        }
                                    });
                        } else {
                            LOGGER.error("Sent ticket to Jira : " + jsonJiraTicket);
                            LOGGER.error("Error when calling URL " + JIRA_HOST.resolve(JIRA_REST_API_URI) + " : " + response.statusCode() + response.statusMessage() + ". Error when creating Jira ticket.");
                            response.bodyHandler(event -> LOGGER.error("Jira error response :" + event.toString()));
                            handler.handle(new Either.Left<>("999;Error when creating Jira ticket"));
                        }
                    });
            createTicketRequest
                    .setChunked(true)
                    .write(jsonJiraTicket.encode());

            terminateRequest(createTicketRequest);
        } catch (Error e) {
            LOGGER.error("Error when creating Jira ticket",e );
            handler.handle(new Either.Left<>("999;Error when creating Jira ticket: " + e.getMessage()));
        }

    }

    private void updateComments(final HttpClientResponse response, final JsonObject jsonPivot,
                                final JsonArray jsonJiraComments,
                                final Handler<Either<String, JsonObject>> handler) {


        response.bodyHandler(buffer -> {

            JsonObject infoNewJiraTicket = new JsonObject(buffer.toString());
            String idNewJiraTicket = infoNewJiraTicket.getString("key");

            LinkedList<String> commentsLinkedList = new LinkedList<>();

            jsonPivot.put("id_jira", idNewJiraTicket);
            jsonPivot.put("statut_jira", "Nouveau");

            if (jsonJiraComments != null) {
                for (Object comment : jsonJiraComments) {
                    commentsLinkedList.add(comment.toString());
                }
                sendJiraComments(idNewJiraTicket, commentsLinkedList, jsonPivot, handler);
            } else {
                handler.handle(new Either.Right<>(new JsonObject()
                        .put("status", "OK")
                        .put("jsonPivotCompleted", jsonPivot)
                ));
            }

        });


    }

    /**
     * Send Jira Comments
     *
     * @param idJira arrayComments
     */
    private void sendJiraComments(final String idJira, final LinkedList commentsLinkedList, final JsonObject jsonPivot,
                                  final Handler<Either<String, JsonObject>> handler) {


        if (commentsLinkedList.size() > 0) {


            final URI urlNewTicket = JIRA_REST_API_URI.resolve(idJira + "/comment");

            final HttpClientRequest commentTicketRequest = httpClient.post(urlNewTicket.toString(), response -> {

                // If ticket well created, then HTTP Status Code 201: The request has been fulfilled and has resulted in one or more new resources being created.
                if (response.statusCode() != HTTP_STATUS_201_CREATED) {
                    handler.handle(new Either.Left<>("999;Error when add Jira comment : " + commentsLinkedList.getFirst().toString() + " : " + response.statusCode() + " " + response.statusMessage()));
                    LOGGER.error("POST " + JIRA_HOST.resolve(urlNewTicket));
                    LOGGER.error("Error when add Jira comment on " + idJira + " : " + response.statusCode() + " - " + response.statusMessage() + " - " + commentsLinkedList.getFirst().toString());
                    return;
                }
                //Recursive call
                commentsLinkedList.removeFirst();
                sendJiraComments(idJira, commentsLinkedList, jsonPivot, handler);
            });
            commentTicketRequest.setChunked(true);

            final JsonObject jsonCommTicket = new JsonObject();
            jsonCommTicket.put("body", commentsLinkedList.getFirst().toString());
            commentTicketRequest.write(jsonCommTicket.encode());

            terminateRequest(commentTicketRequest);

        } else {
            handler.handle(new Either.Right<>(new JsonObject()
                    .put("status", "OK")
                    .put("jsonPivotCompleted", jsonPivot)
            ));
        }
    }

    /**
     * Send PJ from IWS to JIRA
     *
     * @param jsonPivotCompleted jsonJiraPJ jsonPivot handler
     */
    private void updateJiraPJ(final JsonObject jsonPivotCompleted,
                              final JsonArray jsonJiraPJ,
                              final JsonObject jsonPivot,
                              final Handler<Either<String, JsonObject>> handler) {

        String idJira = jsonPivotCompleted.getString(IDJIRA_FIELD);

        LinkedList<JsonObject> pjLinkedList = new LinkedList<>();

        if (jsonJiraPJ != null && jsonJiraPJ.size() > 0) {
            for (Object o : jsonJiraPJ) {
                if (!(o instanceof JsonObject)) continue;
                JsonObject pj = (JsonObject) o;
                pjLinkedList.add(pj);
            }
            sendJiraPJ(idJira, pjLinkedList, jsonPivotCompleted, handler);
        } else {
            handler.handle(new Either.Right<>(new JsonObject()
                    .put("status", "OK")
                    .put("jsonPivotCompleted", jsonPivot)
            ));
        }

    }

    /**
     * Send Jira PJ
     *
     * @param idJira, pjLinkedList, jsonPivot, jsonPivotCompleted, handler
     */
    private void sendJiraPJ(final String idJira,
                            final LinkedList<JsonObject> pjLinkedList,
                            final JsonObject jsonPivotCompleted,
                            final Handler<Either<String, JsonObject>> handler) {

        if (pjLinkedList.size() > 0) {


            final URI urlNewTicket = JIRA_REST_API_URI.resolve(idJira + "/attachments");


            final HttpClientRequest postAttachmentsRequest = httpClient.post(urlNewTicket.toString(), response -> {

                if (response.statusCode() != HTTP_STATUS_200_OK) {
                    handler.handle(new Either.Left<>("999;Error when add Jira attachment : " + pjLinkedList.getFirst().getString("nom") + " : " + response.statusCode() + " " + response.statusMessage()));
                    LOGGER.error("Error when add Jira attachment" + idJira + pjLinkedList.getFirst().getString("nom"));
                    return;
                }
                //Recursive call
                pjLinkedList.removeFirst();
                sendJiraPJ(idJira, pjLinkedList, jsonPivotCompleted, handler);

            });

            String currentBoundary = generateBoundary();

            postAttachmentsRequest.putHeader("X-Atlassian-Token", "no-check")
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

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try {
                outputStream.write(debBytes);
                outputStream.write(pjBytes);
                outputStream.write(finBytes);
            } catch (IOException e) {
                e.printStackTrace();
            }
            byte[] all = outputStream.toByteArray();

            postAttachmentsRequest.putHeader("Content-Length", all.length + "")
                    .write(Buffer.buffer(all));

            terminateRequest(postAttachmentsRequest);
        } else {
            handler.handle(new Either.Right<>(new JsonObject()
                    .put("status", "OK")
                    .put("jsonPivotCompleted", jsonPivotCompleted)
            ));
        }

    }

    public void updateJiraTicket(final JsonObject jsonPivotIn, final String jiraTicketId,
                                  final Handler<Either<String, JsonObject>> handler) {

        final URI urlGetTicketGeneralInfo = JIRA_REST_API_URI.resolve(jiraTicketId);

        final HttpClientRequest getTicketInfosRequest = httpClient.get(urlGetTicketGeneralInfo.toString(),
                response -> {

                    switch (response.statusCode()) {
                        case (HTTP_STATUS_200_OK):
                            response.bodyHandler(bufferGetInfosTicket -> {
                                JsonObject jsonCurrentTicketInfos = new JsonObject(bufferGetInfosTicket.toString());
                                /*
                                //Is JIRA ticket had been created by IWS ?
                                String jiraTicketIdIWS = jsonCurrentTicketInfos.getJsonObject("fields").getString(JIRA_FIELD.getString("id_iws"));
                                if (jiraTicketIdIWS == null) {
                                    handler.handle(new Either.Left<>("102;Not an IWS ticket."));
                                    return;
                                }



                                //Is JIRA ticket had been created by same IWS issue ?
                                String jsonPivotIdIWS = jsonPivotIn.getString(IDIWS_FIELD);
                                if (!jiraTicketIdIWS.equals(jsonPivotIdIWS)) {
                                    handler.handle(new Either.Left<>("102;JIRA Ticket " + jiraTicketId + " already link with an another IWS issue"));
                                    return;
                                }
                                */


                                //Convert jsonPivotIn into jsonJiraTicket
                                final JsonObject jsonJiraUpdateTicket = new JsonObject();
                                JsonObject fields =  new JsonObject();
                                if(jsonPivotIn.getString(IDIWS_FIELD)!=null)
                                    fields.put(JIRA_FIELD.getString("id_iws"), jsonPivotIn.getString(IDIWS_FIELD));
                                if(jsonPivotIn.getString(IDEXTERNAL_FIELD)!=null)
                                    fields.put(JIRA_FIELD.getString("id_iws"), jsonPivotIn.getString(IDEXTERNAL_FIELD));
                                if(jsonPivotIn.getString(STATUSENT_FIELD)!=null)
                                    fields.put(JIRA_FIELD.getString("status_ent"), jsonPivotIn.getString(STATUSENT_FIELD));
                                if(jsonPivotIn.getString(STATUSIWS_FIELD)!=null)
                                    fields.put(JIRA_FIELD.getString("status_iws"), jsonPivotIn.getString(STATUSIWS_FIELD));
                                if(jsonPivotIn.getString(STATUSEXTERNAL_FIELD)!=null)
                                    fields.put(JIRA_FIELD.getString("status_iws"), jsonPivotIn.getString(STATUSEXTERNAL_FIELD));
                                if(jsonPivotIn.getString(DATE_RESO_FIELD)!=null)
                                    fields.put(JIRA_FIELD.getString("resolution_ent"), jsonPivotIn.getString(DATE_RESO_FIELD));
                                if(jsonPivotIn.getString(DATE_RESOIWS_FIELD)!=null)
                                    fields.put(JIRA_FIELD.getString("resolution_iws"), jsonPivotIn.getString(DATE_RESOIWS_FIELD));
                                if(jsonPivotIn.getString(DESCRIPTION_FIELD)!=null)
                                    fields.put(("description"), jsonPivotIn.getString(DESCRIPTION_FIELD));
                                if(jsonPivotIn.getString(TITLE_FIELD)!=null)
                                    fields.put("summary", jsonPivotIn.getString(TITLE_FIELD));
                                if(jsonPivotIn.getString(CREATOR_FIELD)!=null)
                                    fields.put(JIRA_FIELD.getString("creator"), jsonPivotIn.getString(CREATOR_FIELD));
                                jsonJiraUpdateTicket.put("fields", fields);


                                        //Update Jira
                                final URI urlUpdateJiraTicket = JIRA_REST_API_URI.resolve(jiraTicketId);
                                final HttpClientRequest modifyTicketRequest = httpClient.put(urlUpdateJiraTicket.toString(), modifyResp -> {
                                    if (modifyResp.statusCode() == HTTP_STATUS_204_NO_CONTENT) {

                                        // Compare comments and add only new ones
                                        JsonArray jsonPivotTicketComments = jsonPivotIn.getJsonArray("commentaires", new JsonArray());
                                        JsonArray jsonCurrentTicketComments = jsonCurrentTicketInfos.getJsonObject("fields").getJsonObject("comment").getJsonArray("comments");
                                        JsonArray newComments = extractNewComments(jsonCurrentTicketComments, jsonPivotTicketComments);

                                        LinkedList<String> commentsLinkedList = new LinkedList<>();

                                        if (newComments != null) {
                                            for (Object comment : newComments) {
                                                commentsLinkedList.add(comment.toString());
                                            }
                                            sendJiraComments(jiraTicketId, commentsLinkedList, jsonPivotIn, EitherCommentaires -> {
                                                if (EitherCommentaires.isRight()) {
                                                    JsonObject jsonPivotCompleted = EitherCommentaires.right().getValue().getJsonObject("jsonPivotCompleted");

                                                    // Compare PJ and add only new ones
                                                    JsonArray jsonPivotTicketPJ = jsonPivotIn.getJsonArray("pj", new JsonArray());
                                                    JsonArray jsonCurrentTicketPJ = jsonCurrentTicketInfos.getJsonObject("fields").getJsonArray("attachment");
                                                    JsonArray newPJs = extractNewPJs(jsonCurrentTicketPJ, jsonPivotTicketPJ);
                                                    updateJiraPJ(jsonPivotCompleted, newPJs, jsonPivotIn, handler);

                                                } else {
                                                    handler.handle(new Either.Left<>(
                                                            "Error, when creating PJ."));
                                                }
                                            });
                                        } else {
                                            handler.handle(new Either.Right<>(new JsonObject().put("status", "OK")));
                                        }
                                    } else {
                                        LOGGER.error("Error when calling URL " + urlUpdateJiraTicket + " : " +  modifyResp.statusMessage());
                                        modifyResp.bodyHandler(body -> LOGGER.error(body.toString()));
                                        handler.handle(new Either.Left<>("Error when update Jira ticket information"));
                                    }
                                });

                                modifyTicketRequest.setChunked(true)
                                        .write(jsonJiraUpdateTicket.encode());

                                terminateRequest(modifyTicketRequest);
                            });
                            break;
                        case (HTTP_STATUS_404_NOT_FOUND):
                            handler.handle(new Either.Left<>("101;Unknown JIRA Ticket " + jiraTicketId));
                            break;
                        default:
                            LOGGER.error("Error when calling URL : " + response.statusMessage());
                            response.bodyHandler(event -> LOGGER.error("Jira response : " + event));
                            handler.handle(new Either.Left<>("999;Error when getting Jira ticket information"));
                    }
                }
        );
        terminateRequest(getTicketInfosRequest);
    }

    /**
     * Transform a comment from pivot format, to json
     *
     * @param comment Original full '|' separated string
     * @return JsonFormat with correct metadata (owner and date)
     */
    private JsonObject unserializeComment(String comment) {
        try {
            String[] elements = comment.split(Pattern.quote("|"));
            if (elements.length < 4) {
                return null;
            }
            JsonObject jsonComment = new JsonObject();
            jsonComment.put("id", elements[0].trim());
            jsonComment.put("owner", elements[1].trim());
            jsonComment.put("created", elements[2].trim());
            StringBuilder content = new StringBuilder();
            for (int i = 3; i < elements.length; i++) {
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
     *
     * @param sqlDate date string to format
     * @return formatted date string
     */
    private String getDateFormatted(final String sqlDate, final boolean idStyle) {
        final DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        //df.setTimeZone(TimeZone.getTimeZone("GMT"));
        Date d;
        try {
            d = df.parse(sqlDate);
        } catch (ParseException e) {
            LOGGER.error("Support : error when parsing date");
            e.printStackTrace();
            return "iderror";
        }
        Format formatter;
        if (idStyle) {
            formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        } else {
            formatter = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        }
        return formatter.format(d);
    }

    /**
     * Compare comments of ticket and bugtracker issue.
     * Add every comment to ticket not already existing
     *
     * @param inJiraComments   comments of Jira ticket
     * @param incomingComments comment of Bugtracker issue
     * @return comments that needs to be added in ticket
     */
    private JsonArray extractNewComments(JsonArray inJiraComments, JsonArray incomingComments) {
        JsonArray commentsToAdd = new fr.wseduc.webutils.collections.JsonArray();
        for (Object incomingComment : incomingComments) {
            if (!(incomingComment instanceof String)) continue;
            String rawComment = (String) incomingComment;
            JsonObject issueComment = unserializeComment(rawComment);
            String issueCommentId;

            if (issueComment != null && issueComment.containsKey("id")) {
                issueCommentId = issueComment.getString("id", "");
            } else {
                LOGGER.error("Support : Invalid comment : " + rawComment);
                continue;
            }

            boolean existing = false;
            for (Object jiraComment : inJiraComments) {
                if (!(jiraComment instanceof JsonObject)) continue;
                JsonObject ticketComment = (JsonObject) jiraComment;
                String ticketCommentCreated = ticketComment.getString("created", "").trim();
                String ticketCommentId = getDateFormatted(ticketCommentCreated, true);
                String ticketCommentContent = ticketComment.getString("body", "").trim();
                JsonObject ticketCommentPivotContent = unserializeComment(ticketCommentContent);
                String ticketCommentPivotId = "";
                if (ticketCommentPivotContent != null) {
                    ticketCommentPivotId = ticketCommentPivotContent.getString("id");
                }
                if (issueCommentId.equals(ticketCommentId)
                        || issueCommentId.equals(ticketCommentPivotId)) {
                    existing = true;
                    break;
                }
            }
            if (!existing) {
                commentsToAdd.add(rawComment);
            }
        }
        return commentsToAdd;
    }

    /**
     * Compare PJ.
     * Add every comment to ticket not already existing
     *
     * @param inJiraPJs   PJ of Jira ticket
     * @param incomingPJs PJ of pivot issue
     * @return comments that needs to be added in ticket
     */
    private JsonArray extractNewPJs(JsonArray inJiraPJs, JsonArray incomingPJs) {
        JsonArray pjToAdd = new fr.wseduc.webutils.collections.JsonArray();

        for (Object oi : incomingPJs) {
            if (!(oi instanceof JsonObject)) continue;
            JsonObject pjIssuePivot = (JsonObject) oi;
            String issuePivotName;


            if (pjIssuePivot.containsKey("nom")) {
                issuePivotName = pjIssuePivot.getString("nom", "");
            } else {
                LOGGER.error("Support : Invalid PJ : " + pjIssuePivot);
                continue;
            }

            boolean existing = false;

            for (Object ot : inJiraPJs) {
                if (!(ot instanceof JsonObject)) continue;
                JsonObject pjTicketJiraPJ = (JsonObject) ot;
                String ticketPJName = pjTicketJiraPJ.getString("filename", "").trim();

                if (issuePivotName.equals(ticketPJName)) {
                    existing = true;
                    break;
                }
            }
            if (!existing) {
                pjToAdd.add(pjIssuePivot);
            }
        }

        return pjToAdd;

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

    /**
     * Get Jira ticket infos
     *
     * @param jiraTicketId Jira ticket ID
     */
    public void getFromJira(final HttpServerRequest request, final String jiraTicketId,
                            final Handler<Either<String, JsonObject>> handler) {

        final URI urlGetTicketGeneralInfo = JIRA_REST_API_URI.resolve(jiraTicketId);

        HttpClientRequest httpClientRequestGetInfo = httpClient.get(urlGetTicketGeneralInfo.toString(), response -> {
            response.exceptionHandler(exception -> LOGGER.error("Jira request error : ",exception));
            if (response.statusCode() == HTTP_STATUS_200_OK) {
                response.bodyHandler(bufferGetInfosTicket -> {
                    JsonObject jsonGetInfosTicket = new JsonObject(bufferGetInfosTicket.toString());
                    handler.handle(new Either.Right<>(jsonGetInfosTicket));
                });
            } else {
                LOGGER.error("Error when calling URL : " + JIRA_HOST.resolve(urlGetTicketGeneralInfo) + ":" + response.statusCode() + " " + response.statusMessage());
                response.bodyHandler(bufferGetInfosTicket -> LOGGER.error(bufferGetInfosTicket.toString()));
                handler.handle(new Either.Left<>("Error when gathering Jira ticket information"));
            }
        });

        terminateRequest(httpClientRequestGetInfo);
    }








}