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

package fr.openent.supportpivot.deprecatedservices;

import fr.openent.supportpivot.constants.GlpiConstants;
import fr.openent.supportpivot.constants.PivotConstants;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Base64;

import static fr.openent.supportpivot.constants.PivotConstants.*;

/**
 * Created by mercierq on 09/02/2018.
 * Default implementation for JiraService
 */
public class DefaultGLPIService {

    private final Logger LOGGER = LoggerFactory.getLogger(DefaultGLPIService.class);
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
    private static final int HTTP_STATUS_404_NOT_FOUND= 404;
    private static final int HTTP_STATUS_201_CREATED = 201;

    public DefaultGLPIService(Vertx vertx, JsonObject config) {

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
     * @param uri uri
     * @return Http client
     */
    private HttpClient generateHttpClient(URI uri) {
        HttpClientOptions httpClientOptions = new HttpClientOptions()
                .setDefaultHost(uri.getHost())
                .setDefaultPort((uri.getPort() > 0) ? uri.getPort() : ("https".equals(uri.getScheme()) ?  443 : 80))
                .setVerifyHost(false)
                .setTrustAll(true)
                .setSsl("https".equals(uri.getScheme()))
                .setKeepAlive(true);
        return vertx.createHttpClient(httpClientOptions);
    }

    private void terminateRequest(HttpClientRequest httpClientRequest) {
        httpClientRequest.putHeader("Authorization", "Basic " + encoder.encodeToString(JIRA_AUTH_INFO.getBytes()))
                .setFollowRedirects(true);
        if (!httpClientRequest.headers().contains("Content-Type")) {
            httpClientRequest.putHeader("Content-Type", "application/json");
        }
        httpClientRequest.end();
    }

    /**
     * Modified Jira JSON to prepare to send the email to IWS
     */
    public void convertPivotReponseToJsonGLPI(final JsonObject pivotTicket,
                                              Handler<AsyncResult<JsonObject>> handler) {

        JsonObject glpiTicket = new JsonObject();

        /*pivotTicket.forEach( attribute -> {
            String key = null;
            String value = attribute.getValue().toString();
            switch(attribute.getKey()) {
                case PivotConstants.CREATOR_FIELD :
                    key = GlpiConstants.CREATOR_FIELD;
                    break;
                case PivotConstants.TICKETTYPE_FIELD :
                    value = this.getTypeFromPivotToGLPI(value);
                    if(value != null) key = GLPI_GET_TYPE;
                    break;
                case PivotConstants.TITLE_FIELD :
                    key = GLPI_GET_TITLE;
                    break;
                case PivotConstants.DESCRIPTION_FIELD :
                    key = GLPI_GET_DESCRIPTION;
                    break;
                case PivotConstants.PRIORITY_FIELD :
                    key = GLPI_GET_PRIORITY;
                    value = this.getPriorityFromPivotToGLPI(value);
                    break;
                case PivotConstants.MODULES_FIELD :
//                    value = attribute.getValue()
                    key = GLPI_GET_MODULE;
                    break;
                case PivotConstants.COMM_FIELD :
                    key = GLPI_GET_COMMENT;
                    break;
                case PivotConstants.ATTACHMENT_CONTENT_FIELD :
                case PivotConstants.ATTACHMENT_FIELD :
                    key = GLPI_GET_ATTACHMENTS;
                    break;
                case PivotConstants.STATUSJIRA_FIELD :
                    value = this.getStatusFromPivotToGLPI(value);
                    if(value != null) key = GLPI_GET_STATUS;
                    break;
                case PivotConstants.DATE_CREA_FIELD :
                    key = GLPI_GET_CREATED_AT;
                    break;
                case PivotConstants.DATE_RESOJIRA_FIELD :
                    key = GLPI_GET_SOLVED_AT;
                    break;
                case PivotConstants.TECHNICAL_RESP_FIELD :
                    key = GLPI_GET_ATTRIBUTION_NAME;
                    break;
            }

            if(key != null) {
                glpiTicket.put(key, value);
            }
        });*/

        handler.handle(Future.succeededFuture(glpiTicket));
    }

    private String getTypeFromPivotToGLPI(String value) {
//        if(value == TYPE_JIRA_ANOMALY) return TYPE_ANOMALY;
        return TYPE_ID_GLPI_REQUEST;
    }

    private String getStatusFromPivotToGLPI(String value) {
        String glpiValue = null;
        switch (value) {
            case STATUS_JIRA_NEW :
                glpiValue = "1";
                break;
            case STATUS_JIRA_TODO :
                glpiValue = "2";
                break;
            case STATUS_JIRA_TO_PROVIDE :
            case STATUS_JIRA_TO_WAITING :
                glpiValue = "4";
                break;
            case STATUS_JIRA_RESOLVED_TEST :
            case STATUS_JIRA_RESOLVED :
            case STATUS_JIRA_TO_RECLAIM :
                glpiValue = "5";
                break;
            case STATUS_JIRA_TO_CLOSED :
            case STATUS_JIRA_END :
                glpiValue = "6";
                break;
            default:
                glpiValue = "3";
        }
        return glpiValue;
    }

    private String getPriorityFromPivotToGLPI(String value) {
        if(value == PRIORITY_MINOR) return "3";
        return "6";
    }


}