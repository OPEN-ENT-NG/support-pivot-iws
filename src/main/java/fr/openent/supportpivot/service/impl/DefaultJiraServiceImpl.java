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
import java.util.LinkedList;

/**
 * Created by mercierq on 09/02/2018.
 * Default implementation for JiraService
 */
public class DefaultJiraServiceImpl implements JiraService {


    private final Logger log;
    private final Vertx vertx;
    private final String JIRA_HOST;
    private final String authInfo;
    private final String urlJiraFinal;

    DefaultJiraServiceImpl(Vertx vertx, Container container) {

        this.log = container.logger();
        this.vertx = vertx;
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
     * Send pivot information from IWS -- to Jira
     * A ticket is created with the Jira API with all the json information received
     * @param jsonPivot JSON in pivot format
     */
    public void sendToJIRA(JsonObject jsonPivot, final Handler<Either<String, JsonObject>> handler) {

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

        httpClient.close();

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
                        .putHeader("Authorization", "Basic " + Base64.encodeBytes(authInfo.getBytes()))
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

}
