package fr.openent.supportpivot.model.endpoint;

import fr.openent.supportpivot.constants.GlpiConstants;
import fr.openent.supportpivot.constants.PivotConstants;
import fr.openent.supportpivot.managers.ConfigManager;
import fr.openent.supportpivot.model.ticket.PivotTicket;
import fr.openent.supportpivot.services.GlpiService;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.w3c.dom.*;

import javax.xml.xpath.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


class GlpiEndpoint extends  AbstractEndpoint {

    private  static final String PREFIX_GLPICOMMENTID = "glpi_";

    private GlpiService glpiService;
    private XPath path;

    private static final Logger log = LoggerFactory.getLogger(GlpiEndpoint.class);


    GlpiEndpoint(GlpiService glpiService) {
        this.glpiService = glpiService;
        XPathFactory xpf = XPathFactory.newInstance();
        path = xpf.newXPath();
    }

    @Override
    /**
     * Gathering tickets from GLPI into Pivot format.
     * Then router can dispatch them to appropriated endpoints
     */
    public void trigger(JsonObject data, Handler<AsyncResult<List<PivotTicket>>> handler) {

        sendQueryToGlpi(GlpiEndpointHelper.generateXMLRPCListTicketsQuery(), result -> {
            if (result.succeeded()) {
                Document glpiTickets = result.result();
                convertGlpiXMLTicketsToJsonPivotTickets(glpiTickets, resultJson -> {
                    if (resultJson.succeeded()) {
                        handler.handle(Future.succeededFuture(resultJson.result()));
                    } else {
                        handler.handle(Future.failedFuture(resultJson.cause().getMessage()));
                    }
                });
            } else {
                handler.handle(Future.failedFuture(result.cause().getMessage()));
            }
        });
    }

    @Override
    public void process(JsonObject ticketData, Handler<AsyncResult<PivotTicket>> handler) {
        log.error("GLPI endpoint ignores this operation : process a ticket from GLPI.");
    }

    @Override
    public void send(PivotTicket ticket, Handler<AsyncResult<PivotTicket>> handler) {
        if (ticket.getExternalId() == null) {
            createGlpiTicket(ticket, result -> {
                if (result.succeeded()) {
                    handler.handle(Future.succeededFuture(result.result()));
                } else {
                    handler.handle(Future.failedFuture(result.cause().getMessage()));
                }
            });

        } else {
            updateGlpiTicket(ticket, result -> {
                if (result.succeeded()) {
                    handler.handle(Future.succeededFuture(result.result()));
                } else {
                    handler.handle(Future.failedFuture(result.cause().getMessage()));
                }
            });
        }
    }

    private void createGlpiTicket(PivotTicket ticket, Handler<AsyncResult<PivotTicket>> handler) {
        completeDescription(ticket);
        Document createTicketQuery = GlpiEndpointHelper.generateXMLRPCCreateTicketQuery(ticket);
        glpiService.sendRequest("POST", ConfigManager.getInstance().getGlpiRootUri(), createTicketQuery, result -> {
            if (result.succeeded()) {
                Document xml = result.result();
                glpiService.getIdFromGlpiTicket(xml, handlerId -> {

                    String id = handlerId.result().trim();

                    ticket.setExternalId(id);
                    handler.handle(Future.succeededFuture(ticket));  // warn ENT ticket was created in GLPI even if comment or PJ are KO

                    // threat comments and attachments
                    ticket.getComments().forEach(comment -> {
                        sendQueryToGlpi(GlpiEndpointHelper.generateXMLRPCAddCommentQuery(id, (String) comment), handlerComment -> {
                            if (handlerComment.failed()) {
                                log.warn("Error adding comment to GLPI on ticket " + id + " " + comment, handlerComment.cause());
                            }
                        });

                    ticket.getPjs().forEach(pj -> {
                        sendQueryToGlpi(GlpiEndpointHelper.generateXMLRPCAddAttachmentsQuery(id, (JsonObject) pj), handlerPj -> {
                            if (handlerPj.failed()) {
                                log.warn("Error adding attachment to GLPI on ticket " + id + " attachment " + ((JsonObject) pj).getString(GlpiConstants.ATTACHMENT_NAME_FIELD), handlerPj.cause());
                            }
                        });
                    });

                    });
                });
            } else {
                handler.handle(Future.failedFuture(result.cause().getMessage()));
            }
        });
    }

    private void completeDescription(PivotTicket ticket) {

        String content = ticket.getContent();
        content += "\n----------------------------------------------------------------------------";
        content += "\n identifiant ticket ENT : " + ticket.getId();
        content += "\n date de creation: " + ticket.getCreatedAt();
        if (ticket.getUsers() != null) {
            String[] demandeur = ticket.getUsers().split("\\|");
            if (demandeur.length == 5) {
                content += "\n Demandeur : " + demandeur[0];
                content += "\n  - mail : " + demandeur[1];
                content += "\n  - Profil : " + demandeur[2];
                content += "\n  - Etablissement :" + demandeur[4] + " " + demandeur[3];
            } else {
                content += "\n Demandeur : " + ticket.getUsers();
            }
        }
        ticket.setContent(content);
    }

    private void updateGlpiTicket(PivotTicket sourceTicket, Handler<AsyncResult<PivotTicket>> handler) {
       getTicketById(sourceTicket.getExternalId(), result -> {
            if (result.succeeded()) {
                Document glpiTicket = result.result();

                gatheringNewAttachmentsToAddToGlpi(sourceTicket, glpiTicket);

                gatheringCommentsToAddToGlpi(sourceTicket, glpiTicket);
                handler.handle(Future.succeededFuture(sourceTicket));
            } else {
                handler.handle(Future.failedFuture("updateTicket (getTicket from glpi unsuccessful ) : " + result.cause().getMessage()));
            }
        });
    }

    private void gatheringCommentsToAddToGlpi( PivotTicket sourceTicket, Document glpiTicket) {

        if (sourceTicket.getComments().isEmpty()) return;

        try {
            String glpiCommentsContentXpath = "//methodResponse/params/param/value/struct/member[name='" + GlpiConstants.COMM_FIELD + "']/value/array/data/value/struct/member[name='content']/value/string";
            NodeList glpiCommentsContentNodes = (NodeList) path.evaluate(glpiCommentsContentXpath, glpiTicket, XPathConstants.NODESET);

            ArrayList<String> glpiCommentsId = new ArrayList<>();
            for (int i = 0; i < glpiCommentsContentNodes.getLength(); i++) {
                String comment = glpiCommentsContentNodes.item(i).getFirstChild().getNodeValue();
                String[] comment_values = comment.split("\\|");
                if (comment_values.length == 4) {
                    glpiCommentsId.add(comment_values[0]);
                }
            }

            sourceTicket.getComments().forEach(comment -> {
                String[] comment_values = comment.toString().split("\\|");
                if  ( comment_values.length != 4) {
                    log.warn("Ignoring comment when updating glpi ticket : Bad comment format for ticket" + sourceTicket.getId() + " : " + comment);
                    return; //next comment
                }
                
                String idComment = comment_values[0];

                if (!idComment.startsWith(PREFIX_GLPICOMMENTID)) {
                    //ignore comment from glpi (id starts with "glpi_")
                    if (!glpiCommentsId.contains(idComment)) {
                        sendQueryToGlpi(GlpiEndpointHelper.generateXMLRPCAddCommentQuery(sourceTicket.getExternalId(), comment.toString()), handlerComment -> {
                            if (handlerComment.failed()){
                                log.error("Error when uploading to GLPI a comment  " + idComment  + " for ticket ent_id=" + sourceTicket.getId(), handlerComment.cause());
                            }
                        });
                    }
                }
                });

        } catch (XPathExpressionException e) {
            log.warn("Error when  gathering Comments to add to glpi. Pjs are ignored.", e);
        }

    }

    private void gatheringNewAttachmentsToAddToGlpi(PivotTicket sourceTicket, Document glpiTicket) {

        String attachments_xpath = "//methodResponse/params/param/value/struct/member[name='" + GlpiConstants.ATTACHMENT_FIELD + "']/value/array/data/value/struct/member[name='filename']/value/string";

        try {

            NodeList glpiAttachmentsNameNodes = (NodeList) path.evaluate(attachments_xpath, glpiTicket, XPathConstants.NODESET);
            ArrayList<String> glpiAttachmentsName = new ArrayList<>();
            for (int i = 0; i < glpiAttachmentsNameNodes.getLength(); i++) {
                String attachmentName = glpiAttachmentsNameNodes.item(i).getFirstChild().getNodeValue();
                glpiAttachmentsName.add(attachmentName);
            }

            sourceTicket.getPjs().forEach(attachment -> {
                String attachmentName = ((JsonObject) attachment).getString("nom");
                if (!glpiAttachmentsName.contains(attachmentName)) {

                    sendQueryToGlpi(GlpiEndpointHelper.generateXMLRPCAddAttachmentsQuery(sourceTicket.getExternalId(), (JsonObject) attachment), handlerComment -> {
                        if (handlerComment.failed()) {
                            log.error("Error when uploading to GLPI an attachment  " + attachmentName + " for ticket ent_id=" + sourceTicket.getId(), handlerComment.cause());
                        }
                    });
                }
            });

        } catch (XPathExpressionException e) {
            log.warn("Error when  gathering attachments to add to glpi. attachments are ignored.", e);
        }
    }


    private void sendQueryToGlpi(Document queryxml, Handler<AsyncResult<Document>> handler) {
        glpiService.sendRequest("POST", ConfigManager.getInstance().getGlpiRootUri(), queryxml, result -> {
            if (result.succeeded()) {
                handler.handle(Future.succeededFuture(result.result()));
            } else {
                handler.handle(Future.failedFuture(result.cause()));
            }
        });
    }
    private void getTicketById(String glpiId, Handler<AsyncResult<Document>> handler) {
        sendQueryToGlpi(GlpiEndpointHelper.generateXMLRPCGetTicketQuery(glpiId), handler);
    }

    private void convertGlpiXMLTicketsToJsonPivotTickets(Document xmlTickets, Handler<AsyncResult<List<PivotTicket>>> handler) {
        try {
            String expression = "//methodResponse/params/param/value/array/data/value/struct/member[./name='is_deleted'][./value/string='0']/..";
            NodeList xmlTicketsNodes = (NodeList) path.evaluate(expression, xmlTickets, XPathConstants.NODESET);


            List<Future> futures = new ArrayList<>();
            for (int i = 0; i < xmlTicketsNodes.getLength(); i++) {
                Future<PivotTicket> future = Future.future();
                futures.add(future);
                Node ticket = xmlTicketsNodes.item(i);
                downloadGlpiTicketInJsonPivotFormat(ticket, result -> {
                    if (result.succeeded()) {
                        future.complete(result.result());
                    } else {
                        String message = "A ticket ("+ ticket +") can not be parsed: " + result.cause().getMessage();
                        log.warn(message);
                        future.fail(message);
                    }
                });
            }

            List<PivotTicket> pivotTickets = new ArrayList<>();
            CompositeFuture.join(futures).setHandler(event -> {
                if (event.succeeded()) {
                    for (Future future : futures)  {
                        if (future.succeeded() && future.result() != null) {
                            pivotTickets.add((PivotTicket) future.result());
                        }
                    }
                    handler.handle(Future.succeededFuture(pivotTickets));
                } else {
                    handler.handle(Future.failedFuture(event.cause().getMessage()));
                }
            });
        } catch (Exception e) {
            handler.handle(Future.failedFuture(e.getMessage()));
        }
    }


    private void downloadGlpiTicketInJsonPivotFormat(Node nodeTicket, Handler<AsyncResult<PivotTicket>> handler) {
        PivotTicket pivotTicket = new PivotTicket();

        String id = glpiService.getIdFromGlpiTicket(nodeTicket);

        getTicketById(id, resultTicket -> {
            if (resultTicket.succeeded()) {

                try {
                    XPathExpression expr = path.compile("//methodResponse/params/param/value/struct/member");
                    NodeList fields = (NodeList) expr.evaluate(resultTicket.result(), XPathConstants.NODESET);

                    ArrayList<Future> futures = new ArrayList<>();
                    for (int j = 0; j < fields.getLength(); j++) {
                        Future future = Future.future();
                        futures.add(future);

                        String name = (String) path.compile("name").evaluate(fields.item(j), XPathConstants.STRING);
                        Node value = (Node) path.compile("value").evaluate(fields.item(j), XPathConstants.NODE);
                        MapGlpiTicketFieldIntoPivotTicket(name, value, pivotTicket, id, future);
                    }

                    CompositeFuture.join(futures).setHandler(event ->
                        handler.handle(Future.succeededFuture(pivotTicket))
                    );

                } catch (XPathExpressionException e) {
                    handler.handle(Future.failedFuture(e.getMessage()));
                }
            } else {
                handler.handle(Future.failedFuture(resultTicket.cause().getMessage()));
            }
        });
    }

    private void MapGlpiTicketFieldIntoPivotTicket(String name, Node value, PivotTicket pivotTicket, String ticketId, Future future) {
        String stringValue = value.getTextContent().trim();
        try {
            switch (name) {
                case GlpiConstants.ID_FIELD:
                    pivotTicket.setExternalId(stringValue);
                    break;
                case GlpiConstants.ID_ENT:
                    pivotTicket.setId(stringValue);
                    break;
                case GlpiConstants.ID_JIRA:
                    pivotTicket.setJiraId(stringValue);
                    break;
                case GlpiConstants.TITLE_FIELD:
                    pivotTicket.setTitle(stringValue);
                    break;
                case GlpiConstants.TICKETTYPE_FIELD:
                    pivotTicket.setType(mapGlpiTypeValueToPivot(stringValue));
                    break;
                case GlpiConstants.STATUS_FIELD:
                    pivotTicket.setStatusExternal(mapGlpiStatusValueToPivot(stringValue));
                    break;
                case GlpiConstants.PRIORITY_FIELD:
                    pivotTicket.setPriority(mapGlpiPriorityValueToPivot(stringValue));
                    break;
                case GlpiConstants.CATEGORIES_FIELD:
                   // pivotTicket.setCategorie(mapGlpiModuleValueToPivot(stringValue));
                    break;
                case GlpiConstants.DESCRIPTION_FIELD:
                    pivotTicket.setContent(stringValue);
                    break;
                case GlpiConstants.USERS_FIELD:
                    convertUsersToPivotCreatorOrAttributed(value, pivotTicket);
                    break;
                case GlpiConstants.COMM_FIELD:
                    pivotTicket.setComments(convertGlpiCommentsToPivotComments(value));
                    break;
                    /*
                case GlpiConstants.DATE_CREA_FIELD:
                  pivotTicket.setGlpiCreatedAt(stringValue);
                    break;
                case GlpiConstants.DATE_UPDATE_FIELD:
                   pivotTicket.setGlpiUpdatedAt(stringValue);
                    break;
                case GlpiConstants.DATE_RESO_FIELD:
                   pivotTicket.setGlpiSolvedAt(stringValue);
                    break;
                     */
                case GlpiConstants.ATTACHMENT_FIELD:
                    convertGlpiAttachmentsToPivotAttachments(value, pivotTicket, ticketId, future);
                    return;  //exit here to wait result of future pass to treatment
                default:
            }
        } catch (XPathException e) {
            log.info("Impossible to map field " + name + " for glpi ticket "  +  ticketId,e);
        }

        future.complete();
    }

    private void convertUsersToPivotCreatorOrAttributed(Node value, PivotTicket pivotTicket) throws XPathExpressionException {
        XPathExpression expr = path.compile("struct/member");
        NodeList userTypes = (NodeList) expr.evaluate(value, XPathConstants.NODESET);
        for (int i = 0; i < userTypes.getLength(); i++) {
            Node type = userTypes.item(i);
            NodeList nodeTypeName = (NodeList) path.compile("name").evaluate(type, XPathConstants.NODESET);
            String typeName = nodeTypeName.item(0).getTextContent();
            expr = path.compile("value/array/data/value/struct/member");
            NodeList userFields = (NodeList) expr.evaluate(type, XPathConstants.NODESET);
            for (int j = 0; j < userFields.getLength(); j++) {
                NodeList requesterFieldName = (NodeList) path.compile("name").evaluate(userFields.item(j), XPathConstants.NODESET);
                NodeList requesterFieldValue = (NodeList) path.compile("value").evaluate(userFields.item(j), XPathConstants.NODESET);

                if (requesterFieldName.item(0).getTextContent().equals(GlpiConstants.ATTRIBUTION_FIELD)) {
                    switch (typeName) {
                        case GlpiConstants.REQUESTER_FIELD:
                            pivotTicket.setCreator(requesterFieldValue.item(0).getTextContent());
                            break;
                        case GlpiConstants.ASSIGN_FIELD:
                            pivotTicket.setAttributed(requesterFieldValue.item(0).getTextContent().trim());
                            break;
                    }
                }
            }
        }
    }


    private String mapGlpiModuleValueToPivot(String pivotValue) {
        /*
        TODO
        if (GlpiConstants.MODULE_NAME.contains(pivotValue)) {
            return pivotValue;
        }
        return GlpiConstants.MODULE_OTHER;*/
        return "";
    }

    private String mapGlpiTypeValueToPivot(String pivotValue) {
        if (Objects.equals(pivotValue, GlpiConstants.TYPE_INCIDENT)) {
            return PivotConstants.TYPE_INCIDENT;
        }

        return PivotConstants.TYPE_REQUEST;
    }

    private String mapGlpiStatusValueToPivot(String pivotValue) {
        switch (pivotValue) {
            case GlpiConstants.STATUS_NEW:
                return PivotConstants.STATUS_NEW;
            case GlpiConstants.STATUS_RESOLVED:
                return PivotConstants.STATUS_RESOLVED;
            case GlpiConstants.STATUS_CLOSED:
                return PivotConstants.STATUS_CLOSED;
        }
        return PivotConstants.STATUS_OPENED;
    }

    private String mapGlpiPriorityValueToPivot(String pivotValue) {
        if (GlpiConstants.PRIORITY_LEVEL_MINOR.contains(pivotValue)) {
            return PivotConstants.PRIORITY_MINOR;
        }

        return PivotConstants.PRIORITY_MAJOR;
    }

    private JsonArray convertGlpiCommentsToPivotComments(Node followups) throws XPathExpressionException {
        NodeList comments = (NodeList) path.compile("array/data/value/struct").evaluate(followups, XPathConstants.NODESET);

        JsonArray listComments = new JsonArray();

        for (int i = 0; i < comments.getLength(); i++) {
            JsonObject comment = new JsonObject();
            NodeList commentFields = (NodeList) path.compile("member").evaluate(comments.item(i), XPathConstants.NODESET);
            for (int j = 0; j < commentFields.getLength(); j++) {
                Node commFieldName = (Node) path.compile("name").evaluate(commentFields.item(j), XPathConstants.NODE);
                Node commFieldValue = (Node) path.compile("value").evaluate(commentFields.item(j), XPathConstants.NODE);
                String contentValue = commFieldValue.getTextContent();
                switch (commFieldName.getTextContent()) {
                    case GlpiConstants.COMM_ID_FIELD:
                        comment.put(GlpiConstants.COMM_ID_FIELD, contentValue.trim());
                        break;
                    case GlpiConstants.COMM_USER_NAME_FIELD:
                        comment.put(GlpiConstants.COMM_USER_NAME_FIELD, contentValue.trim());
                        break;
                    case GlpiConstants.COMM_CONTENT_FIELD:
                        comment.put(GlpiConstants.COMM_CONTENT_FIELD, contentValue.trim());
                        break;
                    case GlpiConstants.DATE_CREA_FIELD:
                        comment.put(GlpiConstants.DATE_CREA_FIELD, contentValue.trim());
                        break;
                }
            }

            String commentContent = comment.getString(GlpiConstants.COMM_CONTENT_FIELD);
            if (commentContent.matches("(?s)^.*\\|.*\\|.*\\|.*$")) {
                listComments.add(commentContent);
            }else{
                String formattedComment = PREFIX_GLPICOMMENTID

                        + comment.getString(GlpiConstants.COMM_ID_FIELD) + "|"
                        +  comment.getString(GlpiConstants.COMM_USER_NAME_FIELD) + "|"
                        +   comment.getString(GlpiConstants.DATE_CREA_FIELD) + "|\n"
                        + commentContent;
                
                listComments.add(formattedComment);
            }

        }
        return listComments;
    }

    private void convertGlpiAttachmentsToPivotAttachments(Node attachments, PivotTicket pivotTicket, String ticketId, Future future) throws XPathExpressionException {
        NodeList attachmentsNodes = (NodeList) path.compile("array/data/value/struct").evaluate(attachments, XPathConstants.NODESET);
        List<Future> futures = new ArrayList<>();

            for (int i = 0; i < attachmentsNodes.getLength(); i++) {
                NodeList attachmentFields = (NodeList) path.compile("member").evaluate(attachmentsNodes.item(i), XPathConstants.NODESET);
                PivotTicket.Attachment pivotAttachment = new PivotTicket.Attachment();
                for (int j = 0; j < attachmentFields.getLength(); j++) {

                    Node attachmentFieldName = (Node) path.compile("name").evaluate(attachmentFields.item(j), XPathConstants.NODE);
                    Node attchmentFieldValue = (Node) path.compile("value").evaluate(attachmentFields.item(j), XPathConstants.NODE);

                    String contentValue = attchmentFieldValue.getTextContent().trim();
                    switch (attachmentFieldName.getTextContent()) {
                        case GlpiConstants.ATTACHMENT_NAME_FIELD:
                            pivotAttachment.setName(contentValue);
                            break;
                        case GlpiConstants.ATTACHMENT_ID_FIELD:
                            Future<Boolean> attachementFuture = Future.future();
                            futures.add(attachementFuture);

                            sendQueryToGlpi(GlpiEndpointHelper.generateXMLRPCGetDocumentQuery(ticketId, contentValue), resultAttachment -> {
                                if (resultAttachment.succeeded()) {
                                    try {
                                        String base64Content = (String) path.compile("//methodResponse/params/param/value/struct/member[name='base64']/value/string")
                                                .evaluate(resultAttachment.result(), XPathConstants.STRING);
                                        pivotAttachment.setContent(base64Content);
                                    } catch (XPathException xpe) {
                                        log.warn("Error when getting GLPI attachment " + contentValue + " for ticket " + ticketId , xpe);
                                    }
                                }else {
                                    log.warn("Error when getting GLPI attachment " + contentValue + " for ticket " + ticketId , resultAttachment.cause());
                                }
                                attachementFuture.complete();
                            });
                            break;
                    }
                }
                pivotTicket.addPj(pivotAttachment);
            }

        CompositeFuture.join(futures).setHandler(event -> {
            if (event.failed()) {
                log.error("An error occured while getting attachments: " + event.cause().getMessage());
            }
           future.complete();
        });

    }
}
