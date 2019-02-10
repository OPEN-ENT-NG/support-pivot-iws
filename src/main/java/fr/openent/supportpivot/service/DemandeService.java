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

package fr.openent.supportpivot.service;

import fr.wseduc.webutils.Either;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonObject;

/**
 * Created by colenot on 07/12/2017.
 *
 * Service to handle pivot information and send at the right place
 */
public interface DemandeService {

    /**
     * Add issue from IWS
     * Check every mandatory field is present in jsonPivot, then send for treatment
     * @param jsonPivot JSON object in PIVOT format
     */
    void treatTicketFromIWS(HttpServerRequest request, JsonObject jsonPivot, Handler<Either<String, JsonObject>> handler);

    /**
     * Add issue from ENT
     * - Check every mandatory field is present in jsonPivot, then send for treatment
     * - Replace empty values by default ones
     * - Replace modules names
     * @param jsonPivot JSON object in PIVOT format
     */
    void treatTicketFromENT(JsonObject jsonPivot, Handler<Either<String, JsonObject>> handler);

    /**
     * Send an issue to IWS with fictive info, for testing purpose
     * @param mailTo mail to send to
     */
    void getMongoInfos(HttpServerRequest request, String mailTo, Handler<Either<String, JsonObject>> handler);

    /**
     * Send updated informations from a Jira ticket to IWS
     * @param idJira idJira updated in Jira to sens to IWS
     */
    void sendJiraTicketToIWS(HttpServerRequest request, String idJira, Handler<Either<String, JsonObject>> handler);
}
