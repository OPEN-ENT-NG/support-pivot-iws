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

import fr.openent.supportpivot.managers.ConfigManager;
import fr.openent.supportpivot.services.MongoService;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.email.EmailSender;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static fr.wseduc.webutils.Server.getEventBus;
import static fr.wseduc.webutils.Utils.handlerToAsyncHandler;
import static fr.openent.supportpivot.constants.PivotConstants.*;
import static fr.openent.supportpivot.model.ticket.PivotTicket.*;

/**
 * Created by colenot on 07/12/2017.
 *
 * Default implementation for DemandeService
 */
public class DefaultDemandeServiceImpl implements DemandeService {

    private final EmailSender emailSender;
    private final Logger log = LoggerFactory.getLogger(DefaultDemandeServiceImpl.class);

    private final EventBus eb;

    private final String MAIL_IWS;
    private final String COLLECTIVITY_NAME;
    private final String ATTRIBUTION_DEFAULT;
    private final String TICKETTYPE_DEFAULT;
    private final String PRIORITY_DEFAULT;

    /**
     * Mandatory fields
     */
    public static final String[] IWS_MANDATORY_FIELDS = {
            IDIWS_FIELD,
            COLLECTIVITY_FIELD,
            CREATOR_FIELD,
            DESCRIPTION_FIELD,
            ATTRIBUTION_FIELD
    };

    public static final String[] ENT_MANDATORY_FIELDS = {
            ID_FIELD,
            CREATOR_FIELD,
            TITLE_FIELD,
            DESCRIPTION_FIELD
    };

    private final DefaultJiraServiceImpl jiraService;
    private MongoService mongoService;

    private static final String ENT_TRACKERUPDATE_ADDRESS = "support.update.bugtracker";

    public DefaultDemandeServiceImpl(Vertx vertx, JsonObject config, EmailSender emailSender,
                                     MongoService mongoService) {
        this.emailSender = emailSender;
        this.mongoService = mongoService;

        eb = getEventBus(vertx);
        this.MAIL_IWS = config.getString("mail-iws");
        this.COLLECTIVITY_NAME = ConfigManager.getInstance().getCollectivity();
        this.ATTRIBUTION_DEFAULT = config.getString("default-attribution");
        this.TICKETTYPE_DEFAULT = config.getString("default-tickettype");
        this.PRIORITY_DEFAULT = config.getString("default-priority");
        this.jiraService = new DefaultJiraServiceImpl(vertx, config);
    }

    /**
     * Add issue from IWS
     * Check every mandatory field is present in jsonPivot, then send for treatment
     * @param jsonPivot JSON object in PIVOT format
     */
    public void treatTicketFromIWS(HttpServerRequest request, JsonObject jsonPivot, final Handler<Either<String, JsonObject>> handler) {

        mongoService.saveTicket(ATTRIBUTION_IWS, jsonPivot);

        StringBuilder missingMandatoryFields = new StringBuilder();
        for (String field : IWS_MANDATORY_FIELDS) {
            if (!jsonPivot.containsKey(field)) {
                missingMandatoryFields.append(field);
                missingMandatoryFields.append(", ");
            }
        }

        if (missingMandatoryFields.length() != 0) {
            handler.handle(new Either.Left<>("2;Mandatory Field " + missingMandatoryFields));
        } else {
            add(request, jsonPivot, ATTRIBUTION_IWS, handler);
        }

    }

    /**
     * Add issue from ENT
     * - Check every mandatory field is present in jsonPivot, then send for treatment
     * - Replace empty values by default ones
     * - Replace modules names
     *
     * @param jsonPivot JSON object in PIVOT format
     */
    public void treatTicketFromENT(JsonObject jsonPivot, final Handler<Either<String, JsonObject>> handler) {

        mongoService.saveTicket(ATTRIBUTION_ENT, jsonPivot);

        StringBuilder missingMandatoryFields = new StringBuilder();
        for (String field : ENT_MANDATORY_FIELDS) {
            if (!jsonPivot.containsKey(field)) {
                missingMandatoryFields.append(field);
                missingMandatoryFields.append(", ");
            }
        }

        if (missingMandatoryFields.length() != 0) {
            handler.handle(new Either.Left<>("2;Mandatory Field " + missingMandatoryFields));

        } else {
            if (!jsonPivot.containsKey(COLLECTIVITY_FIELD)) {
                jsonPivot.put(COLLECTIVITY_FIELD, COLLECTIVITY_NAME);
            }
            if (!jsonPivot.containsKey(ATTRIBUTION_FIELD)) {
                jsonPivot.put(ATTRIBUTION_FIELD, ATTRIBUTION_DEFAULT);
            }
            if (!jsonPivot.containsKey(TICKETTYPE_FIELD)) {
                jsonPivot.put(TICKETTYPE_FIELD, TICKETTYPE_DEFAULT);
            }
            if (!jsonPivot.containsKey(PRIORITY_FIELD)) {
                jsonPivot.put(PRIORITY_FIELD, PRIORITY_DEFAULT);
            }
            if (jsonPivot.containsKey(DATE_CREA_FIELD)) {
                String sqlDate = jsonPivot.getString(DATE_CREA_FIELD);
                jsonPivot.put(DATE_CREA_FIELD, formatSqlDate(sqlDate));
            }
            if (jsonPivot.containsKey(STATUSENT_FIELD)
                    && jsonPivot.getString(STATUSENT_FIELD) != null
                    && !jsonPivot.getString(STATUSENT_FIELD).isEmpty()) {
                String newStatus;
                switch (jsonPivot.getString(STATUSENT_FIELD)) {
                    case STATUS_NEW:
                        newStatus = STATUS_NEW;
                        break;
                    case STATUS_OPENED:
                        newStatus = STATUS_OPENED;
                        break;
                    case STATUS_RESOLVED:
                        newStatus = STATUS_RESOLVED;
                        break;
                    case STATUS_CLOSED:
                        newStatus = STATUS_CLOSED;
                        break;
                    default:
                        newStatus = jsonPivot.getString(STATUSENT_FIELD);
                }
                jsonPivot.put(STATUSENT_FIELD, newStatus);

            }

            JsonArray modules = jsonPivot.getJsonArray(MODULES_FIELD, new fr.wseduc.webutils.collections.JsonArray());
            JsonArray newModules = new fr.wseduc.webutils.collections.JsonArray();
            for (Object o : modules) {
                if (o instanceof String) {
                    String module = (String) o;
                    newModules.add(moduleEntToPivot(module));
                }
            }
            jsonPivot.put(MODULES_FIELD, newModules);
            add(null, jsonPivot, ATTRIBUTION_ENT, handler);
        }
    }

    /**
     * Get a modules name ENT-like, returns the module name PIVOT-like
     * Returns original name by default
     *
     * @param moduleName ENT-like module name
     * @return PIVOT-like module name encoded in UTF-8
     */
    private String moduleEntToPivot(String moduleName) {
        return APPLICATIONS_MAP.getOrDefault(moduleName, "Autres");
    }

    /**
     * Format a Date from SQL-like format to dd/MM/yyyy
     *
     * @param sqlDate date to format
     * @return The date formatted, or the original date is case of error
     */
    private String formatSqlDate(String sqlDate) {
        SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
        SimpleDateFormat output = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
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
     * Save the ticket to mongo before processing
     *
     * @param jsonPivot Json in pivot format
     */
    private void add(final HttpServerRequest request, JsonObject jsonPivot,
                     final String source, final Handler<Either<String, JsonObject>> handler) {

        String attribution = jsonPivot.getString(ATTRIBUTION_FIELD);
        if (!ATTRIBUTION_CGI.equals(attribution)
                && !ATTRIBUTION_ENT.equals(attribution)
                && !ATTRIBUTION_IWS.equals(attribution)) {
            handler.handle(new Either.Left<>("3;Invalid value for " + ATTRIBUTION_FIELD));
            return;
        }

        switch (source) {
            case ATTRIBUTION_IWS:
                boolean sentToEnt = false;
                if (jsonPivot.containsKey(ID_FIELD)
                        && !jsonPivot.getString(ID_FIELD).isEmpty()) {
                    sendToENT(jsonPivot, handler);
                    sentToEnt = true;
                }
                //TODO  ici il y a un problème : l'état de la maj de l'ENt est renvoyée vers IWS mais l'algo se proursuit
                //vers la maj de JIRA. ENT peut être KO, mais JIRA se mete à jour quand même, ou alors JIRA est KO mais IWS n'est pas averti
                if (ATTRIBUTION_CGI.equals(attribution) || (jsonPivot.containsKey(IDJIRA_FIELD)
                        && !jsonPivot.getString(IDJIRA_FIELD).isEmpty())) {
                    Handler<Either<String, JsonObject>> newHandler;
                    if (sentToEnt) {
                        newHandler = jiraResponse -> {
                            if (jiraResponse.isLeft()) {
                                log.error("Supportpivot : could not save ticket to JIRA");
                            }
                        };
                    } else {
                        newHandler = handler;
                    }
                    sendToJIRA(request, jsonPivot, newHandler);
                }
                break;

            case ATTRIBUTION_ENT:
                sendToIWS(request, jsonPivot, handler);
                if (jsonPivot.containsKey(IDJIRA_FIELD)
                        && jsonPivot.getString(IDJIRA_FIELD).isEmpty()) {
                    sendToJIRA(request, jsonPivot, jiraResponse -> {
                        if (jiraResponse.isLeft()) {
                            log.error("Supportpivot : could not save ticket to JIRA");
                        }
                    });
                }
                break;

            case ATTRIBUTION_CGI:
                sendToIWS(request, jsonPivot, handler);
                if (jsonPivot.containsKey(ID_FIELD)
                        && jsonPivot.getString(ID_FIELD).isEmpty()) {
                    sendToENT(jsonPivot, entResponse -> {
                        if (entResponse.isLeft()) {
                            log.error("Supportpivot : could not send JIRA ticket to ENT" + entResponse.left().getValue());
                        }
                    });
                }
                break;
        }
    }


    /**
     * Send pivot information to ENT -- using internal bus
     *
     * @param jsonPivot JSON in pivot format
     */
    public void sendToENT(JsonObject jsonPivot, final Handler<Either<String, JsonObject>> handler) {

        if (!jsonPivot.containsKey(ID_FIELD)
                || jsonPivot.getString(ID_FIELD).isEmpty()) {
            handler.handle(new Either.Left<>("2;Mandatory field " + ID_FIELD));
            return;
        }
        eb.send(ENT_TRACKERUPDATE_ADDRESS,
                new JsonObject().put("action", "create").put("issue", jsonPivot),
                handlerToAsyncHandler(message -> {
                    if ("OK".equals(message.body().getString("status"))) {
                        handler.handle(new Either.Right<>(message.body()));
                    } else {
                        handler.handle(new Either.Left<>("999;" + message.body().toString()));
                    }
                }));
    }

    /**
     * Send pivot information to JIRA
     *
     * @param jsonPivotIn JSON in pivot format
     */
    public void sendToJIRA(JsonObject jsonPivotIn, final Handler<AsyncResult<JsonObject>> handler) {
        jiraService.sendToJIRA(jsonPivotIn, result -> {
            if (result.isRight()) {
                handler.handle(Future.succeededFuture(result.right().getValue().getJsonObject("jsonPivotCompleted")));
            } else {
                handler.handle(Future.failedFuture(result.left().getValue()));
            }
        });
    }


    /**
     * Send pivot information to JIRA
     *
     * @param jsonPivotIn JSON in pivot format
     */
    private void sendToJIRA(final HttpServerRequest request, JsonObject jsonPivotIn,
                            final Handler<Either<String, JsonObject>> handler) {
        final boolean ticketAlreadyExistsInJira = (jsonPivotIn.containsKey(IDJIRA_FIELD)
                && !jsonPivotIn.getString(IDJIRA_FIELD).isEmpty());
        jiraService.sendToJIRA(jsonPivotIn, eitherJsonPivotOut -> {
            if (eitherJsonPivotOut.isRight()) {
                if (!ticketAlreadyExistsInJira) {

                    //return to IWS ticket with Id_JIRA
                    sendToIWS(request,
                            eitherJsonPivotOut.right().getValue().getJsonObject("jsonPivotCompleted"),
                            handler);
                } else {
                    handler.handle(new Either.Right<>(new JsonObject().put("status", "OK")));
                }
            } else {
                handler.handle(eitherJsonPivotOut);
            }
        });
    }

    private String safeField(String inField) {
        if (inField == null) return null;
        return inField.replace("=", " =");
    }

    /**
     * Send pivot information to IWS -- by mail
     * Every data must be htmlEncoded because of encoding / mails incompatibility
     *
     * @param jsonPivot JSON in pivot format
     */
    private void sendToIWS(HttpServerRequest request, JsonObject jsonPivot, final Handler<Either<String, JsonObject>> handler) {

        StringBuilder mail = new StringBuilder()
                .append("<br/>Collectivite=")
                .append(jsonPivot.getString(COLLECTIVITY_FIELD))
                .append("<br/>academie=")
                .append(jsonPivot.getString(ACADEMY_FIELD, ""))
                .append("<br/>demandeur=")
                .append(safeField(jsonPivot.getString(CREATOR_FIELD)))
                .append("<br/>type_demande=")
                .append(jsonPivot.getString(TICKETTYPE_FIELD, ""))
                .append("<br/>titre=")
                .append(safeField(jsonPivot.getString(TITLE_FIELD)))
                .append("<br/>description=")
                .append(safeField(jsonPivot.getString(DESCRIPTION_FIELD)))
                .append("<br/>priorite=")
                .append(jsonPivot.getString(PRIORITY_FIELD, ""));

        JsonArray modules = jsonPivot.getJsonArray(MODULES_FIELD, new fr.wseduc.webutils.collections.JsonArray());
        mail.append("<br/>modules=[");
        for (int i = 0; i < modules.size(); i++) {
            if (i > 0) {
                mail.append(", ");
            }
            mail.append(modules.getString(i));
        }
        mail.append("]");

        mail.append("<br/>id_jira=")
                .append(jsonPivot.getString(IDJIRA_FIELD, ""))
                .append("<br/>id_ent=")
                .append(jsonPivot.getString(ID_FIELD, ""))
                .append("<br/>id_iws=")
                .append(jsonPivot.getString(IDIWS_FIELD, ""));

        JsonArray comm = jsonPivot.getJsonArray(COMM_FIELD, new fr.wseduc.webutils.collections.JsonArray());
        for (int i = 0; i < comm.size(); i++) {
            mail.append("<br/>commentaires=")
                    .append(safeField(comm.getString(i)));
        }

        mail.append("<br/>statut_iws=")
                .append(jsonPivot.getString(STATUSIWS_FIELD, ""))
                .append("<br/>statut_ent=")
                .append(jsonPivot.getString(STATUSENT_FIELD, ""))
                .append("<br/>statut_jira=")
                .append(jsonPivot.getString(STATUSJIRA_FIELD, ""))
                .append("<br/>date_creation=")
                .append(jsonPivot.getString(DATE_CREA_FIELD, ""))
                .append("<br/>date_resolution_iws=")
                .append(jsonPivot.getString(DATE_RESOIWS_FIELD, ""))
                .append("<br/>date_resolution_ent=")
                .append(jsonPivot.getString(DATE_RESO_FIELD, ""))
                .append("<br/>date_resolution_jira=")
                .append(jsonPivot.getString(DATE_RESOJIRA_FIELD, ""))
                .append("<br/>reponse_technique=")
                .append(safeField(jsonPivot.getString(TECHNICAL_RESP_FIELD, "")))
                .append("<br/>reponse_client=")
                .append(safeField(jsonPivot.getString(CLIENT_RESP_FIELD, "")))
                .append("<br/>attribution=")
                .append(jsonPivot.getString(ATTRIBUTION_FIELD))
                .append("<br/>");

        String mailTo = jsonPivot.getString("email");
        if (mailTo == null || mailTo.isEmpty()) {
            mailTo = this.MAIL_IWS;
        }


        //prepare storage in mongo of sent mail to IWS
        JsonObject savedInfo = new JsonObject();
        savedInfo.put("mailContent", mail.toString());
        savedInfo.put(IDIWS_FIELD, jsonPivot.getString(IDIWS_FIELD, ""));
        savedInfo.put(ID_FIELD, jsonPivot.getString(ID_FIELD, ""));
        savedInfo.put(IDJIRA_FIELD, jsonPivot.getString(IDJIRA_FIELD, ""));
        savedInfo.put("dest", mailTo);

        JsonArray mailAtts = new fr.wseduc.webutils.collections.JsonArray();
        JsonArray savedInfoPJ = new fr.wseduc.webutils.collections.JsonArray();
        JsonArray atts = jsonPivot.getJsonArray(ATTACHMENT_FIELD, new fr.wseduc.webutils.collections.JsonArray());
        for (Object o : atts) {
            if (!(o instanceof JsonObject)) continue;
            JsonObject jsonAtt = (JsonObject) o;
            if (!jsonAtt.containsKey(ATTACHMENT_NAME_FIELD)
                    || !jsonAtt.containsKey(ATTACHMENT_CONTENT_FIELD)) continue;
            JsonObject att = new JsonObject();
            att.put("name", jsonAtt.getString(ATTACHMENT_NAME_FIELD));
            att.put("content", jsonAtt.getString(ATTACHMENT_CONTENT_FIELD));
            mailAtts.add(att);

            savedInfoPJ.add(new JsonObject().put("pj_name", jsonAtt.getString(ATTACHMENT_NAME_FIELD)));
        }
        savedInfo.put("pj", savedInfoPJ);

        //store in mongo sent mail
        mongoService.saveTicket("mail", savedInfo);

        if (emailSender == null) {
            handler.handle(new Either.Left<>("999;EmailSender module not found"));
            return;
        }

        emailSender.sendEmail(request,
                mailTo,
                null,
                null,
                "TICKETCGI",
                mailAtts,
                mail.toString(),
                null,
                false,
                handlerToAsyncHandler(jsonObjectMessage -> handler.handle(new Either.Right<String, JsonObject>(jsonObjectMessage.body()))));
    }

    /**
     * Send updated informations from a Jira ticket to IWS
     *
     * @param idJira idJira updated in Jira to send to IWS
     */
    @Override
    public void sendJiraTicketToIWS(final HttpServerRequest request,
                                    final String idJira,
                                    final Handler<Either<String, JsonObject>> handler) {


        jiraService.getFromJira(request, idJira, stringJsonObjectEither -> {
            if (stringJsonObjectEither.isRight()) {
                JsonObject jsonPivot = stringJsonObjectEither.right().getValue();
                mongoService.saveTicket(ATTRIBUTION_CGI, jsonPivot);
                add(request, jsonPivot, ATTRIBUTION_CGI, handler);
            } else {
                handler.handle(new Either.Left<>(
                        "Error, the ticket has not been sent, it doesn't exist."));
            }
        });

    }


}
