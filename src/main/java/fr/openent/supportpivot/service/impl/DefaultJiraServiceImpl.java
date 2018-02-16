package fr.openent.supportpivot.service.impl;

import fr.openent.supportpivot.Supportpivot;
import fr.openent.supportpivot.service.JiraService;
import fr.wseduc.webutils.Either;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.HttpHeaders;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.json.impl.Base64;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.platform.Container;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.*;

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


    private final JsonObject JIRA_FIELD;


    DefaultJiraServiceImpl(Vertx vertx, Container container) {

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

    }


    /**
     * Send pivot information from IWS -- to Jira
     * A ticket is created with the Jira API with all the json information received
     * @param jsonPivot JSON in pivot format
     */
    public void sendToJIRA(JsonObject jsonPivot, final Handler<Either<String, JsonObject>> handler) {

        String ticketType = "Assistance";

        if (jsonPivot.getString(Supportpivot.TICKETTYPE_FIELD).equals("Anomalie") ||
                jsonPivot.getString(Supportpivot.TICKETTYPE_FIELD).equals("Incident") ||
                jsonPivot.getString(Supportpivot.TICKETTYPE_FIELD).equals("Service")) {
            ticketType = jsonPivot.getString(Supportpivot.TICKETTYPE_FIELD);
        }

        if (!jsonPivot.getString(Supportpivot.IDJIRA_FIELD).isEmpty()) {
            String jiraTicketId = jsonPivot.getString(Supportpivot.IDJIRA_FIELD);
            updateJiraInformations(jsonPivot, jiraTicketId, handler);
        } else {
            final JsonObject jsonJiraTicket = new JsonObject();

            jsonJiraTicket.putObject("fields", new JsonObject()
                    .putObject("project", new JsonObject()
                            .putString("key", jsonPivot.getString(Supportpivot.COLLECTIVITY_FIELD)))
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
     * Update ticket information via Jira API
     */
    private void updateJiraInformations (final JsonObject jsonPivot, final String jiraTicketId,
                                         final Handler<Either<String, JsonObject>> handler) {


        final String urlChangeTransitionTicket = urlJiraFinal + jiraTicketId;

        final JsonObject jsonJiraUpdateTicket = new JsonObject();
        jsonJiraUpdateTicket.putObject("fields", new JsonObject()
                .putString(JIRA_FIELD.getString("status_ent"), jsonPivot.getString(Supportpivot.STATUSENT_FIELD))
                .putString(JIRA_FIELD.getString("status_iws"), jsonPivot.getString(Supportpivot.STATUSIWS_FIELD))
                .putString(JIRA_FIELD.getString("resolution_ent"), jsonPivot.getString(Supportpivot.DATE_RESOENT_FIELD))
                .putString(JIRA_FIELD.getString("resolution_iws"), jsonPivot.getString(Supportpivot.DATE_RESOIWS_FIELD)));

        URI jira_update_infos_URI;
        try {
            jira_update_infos_URI = new URI(urlChangeTransitionTicket);
        } catch (URISyntaxException e) {
            log.error("Invalid jira web service uri", e);
            handler.handle(new Either.Left<String, JsonObject>("Invalid jira url"));
            return;
        }

        final HttpClient httpClient = generateHttpClient(jira_update_infos_URI);


        final HttpClientRequest httpClientRequest = httpClient.put(urlChangeTransitionTicket , new Handler<HttpClientResponse>() {
            @Override
            public void handle(HttpClientResponse response) {
                if (response.statusCode() != 204) {
                    log.error("Error when calling URL : " + response.statusMessage());
                    handler.handle(new Either.Left<String, JsonObject>("Error when update Jira ticket information"));
                }
                else {
                    handler.handle(new Either.Right<String, JsonObject>(new JsonObject().putString("status", "OK")));
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

    /////////////////////////////////////////////////
    // BELOW CODE IS NOT ACTUALLY USED
    // ONLY FOR AUTOMATIC JIRA TRANSITION CHANGE
    /////////////////////////////////////////////////


    /**
     * Get general ticket information via Jira API
     */
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

                    getJiraTicketInformations(jsonPivot, idJira, iwsTicketStatus, new Handler<Either<String, JsonObject>>() {
                        @Override
                        public void handle(Either<String, JsonObject> stringJsonObjectEither) {

                        }
                    });
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