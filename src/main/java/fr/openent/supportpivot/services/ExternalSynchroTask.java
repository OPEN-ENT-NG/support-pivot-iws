/*
 * Copyright (c) Région Ile-de-France, Région Nouvelle-Aquitaine, CGI, 2016.
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

import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;


public class ExternalSynchroTask implements Handler<Long> {

    private final EventBus eb;
    private final Logger log = LoggerFactory.getLogger(ExternalSynchroTask.class);

    private String action;


    public ExternalSynchroTask(EventBus eb) {
        this.eb = eb;
    }


    @Override
    public void handle(Long event) {
        log.info("GLPI cron started : " + action);

        eb.send("supportpivot.glpi.trigger",new JsonObject(),
                res -> {
                    if(res.succeeded()) {
                        log.info("Cron launch successful ");
                    } else {
                        log.error("Cron launch failed " + res.cause());
                    }
                });

    }
}
