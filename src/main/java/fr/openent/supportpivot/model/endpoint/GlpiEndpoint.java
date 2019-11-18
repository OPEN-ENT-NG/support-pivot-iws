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


class GlpiEndpoint implements Endpoint {

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
    public void trigger(JsonObject data, Handler<AsyncResult<List<PivotTicket>>> handler) {
        //recupere les tickets avec toutes les infos
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
        if (ticket.getGlpiId() == null) {
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
        Document createTicketQuery = GlpiEndpointHelper.generateXMLRPCCreateTicketQuery(ticket);
        glpiService.sendRequest("POST", ConfigManager.getInstance().getGlpiRootUri(), createTicketQuery, result -> {
            if (result.succeeded()) {
                Document xml = result.result();
                glpiService.getIdFromGlpiTicket(xml, handlerId -> {

                    String id = handlerId.result().trim();

                    // warn ENT ticket was created in GLPI
                    ticket.setGlpiId(id);
                    ticket.setIwsId(id);
                    handler.handle(Future.succeededFuture(ticket));

                    // threat comments and attachments
                    Document xmlCommentTicket = generateXMLRPCFirstCommentQuery(id, ticket);

                    sendQueryToGlpi(xmlCommentTicket, handlerFirstComment -> {
                        if (handlerFirstComment.succeeded()) {

                            ticket.getComments().forEach(comment -> {
                                sendQueryToGlpi(GlpiEndpointHelper.generateXMLRPCAddCommentQuery(id, (String) comment), handlerComment -> {
                                    if (handlerComment.failed()) {
                                        handler.handle(Future.failedFuture(handlerComment.cause().getMessage()));
                                    }
                                });
                            });

                            ticket.getPjs().forEach(pj -> {
                                sendQueryToGlpi(GlpiEndpointHelper.generateXMLRPCAddAttachmentsQuery(id, (JsonObject) pj), handlerPj -> {
                                    if (handlerPj.failed()) {
                                        handler.handle(Future.failedFuture(handlerPj.cause().getMessage()));
                                    }
                                });
                            });

                        } else {
                            handler.handle(Future.failedFuture(handlerFirstComment.cause().getMessage()));
                        }
                    });
                });
            } else {
                handler.handle(Future.failedFuture(result.cause().getMessage()));
            }
        });
    }

    private Document generateXMLRPCFirstCommentQuery(String Id, PivotTicket ticket) {

        String content = "identifiant ticket ENT : " + ticket.getId()
                + "\n date de creation: " + ticket.getCreatedAt();
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
        return GlpiEndpointHelper.generateXMLRPCAddCommentQuery(Id, content);
    }

    private void updateGlpiTicket(PivotTicket sourceTicket, Handler<AsyncResult<PivotTicket>> handler) {
       getTicketById(sourceTicket.getGlpiId(), result -> {
            if (result.succeeded()) {
                Document glpiTicket = result.result();

                List<Future> futures = new ArrayList<>();

                gatheringNewAttachmentsToAddToGlpi(sourceTicket, glpiTicket, futures);

                gatheringCommentsToAddToGlpi(sourceTicket, glpiTicket, futures);

                CompositeFuture.join(futures).setHandler(event -> {
                    if (event.succeeded()) {
                        handler.handle(Future.succeededFuture(sourceTicket));
                    } else {
                        handler.handle(Future.failedFuture(event.cause().getMessage()));
                    }
                });
            } else {
                handler.handle(Future.failedFuture("updateTicket (getTicket from glpi unsuccessful ) : " + result.cause().getMessage()));
            }
        });
    }

    private void gatheringCommentsToAddToGlpi( PivotTicket sourceTicket, Document glpiTicket, List<Future> futures) {

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

            ArrayList<String> newCommentsFromENTToAddInGlpi = new ArrayList<>();
            sourceTicket.getComments().forEach(comment -> {
                String[] comment_values = comment.toString().split("\\|");
                if  ( comment_values.length != 4) {
                    log.warn("Ignoring comment when updating glpi ticket : Bad comment format for ticket" + sourceTicket.getId() + " : " + comment);
                    return; //next comment
                }
                
                String idComment = comment_values[0];

                if (!idComment.startsWith(PREFIX_GLPICOMMENTID)) {
                    //on ignore les commentaires dont l'identifiant indique qu'il provient de glpi
                    if (!glpiCommentsId.contains(idComment)) {
                        newCommentsFromENTToAddInGlpi.add(comment.toString());
                    }
                }
            });



            //TODO ici en faisant des futures les commentaires ne sont pas créés dans l'ordre
            newCommentsFromENTToAddInGlpi.forEach(commentToAdd -> {
                Future<Boolean> future = Future.future();
                futures.add(future);
                sendQueryToGlpi(GlpiEndpointHelper.generateXMLRPCAddCommentQuery(sourceTicket.getGlpiId(), commentToAdd), handlerComment -> {
                    if (handlerComment.succeeded()) {
                        future.complete();
                    } else {
                        future.fail(handlerComment.cause().getMessage());
                    }
                });
            });
            
        } catch (XPathExpressionException e) {
            log.warn("Error when  gathering Comments to add to glpi. Pjs are ignored.", e);
        }

    }

    private void gatheringNewAttachmentsToAddToGlpi(PivotTicket sourceTicket, Document glpiTicket, List<Future> futures)  {
        //Récupération des pj glpi
        String attachments_xpath = "//methodResponse/params/param/value/struct/member[name='"+ GlpiConstants.ATTACHMENT_FIELD+"']/value";
        try {
            Node attachments_Node = (Node) path.evaluate(attachments_xpath, glpiTicket, XPathConstants.NODE);
        } catch (XPathExpressionException e) {
            log.warn("Error when  gathering Pj to add to glpi. Pjs are ignored.", e);
            return;
        }



                   /* if (ticket.getAttributed() != null) {
                        Future<Boolean> attributed = Future.future();
                        futures.add(attributed);
                        sendTicketGlpi(xmlAttribute(ticket.getGlpiId(), ticket.getAttributed()), handlerAttribute -> {
                            if (handlerAttribute.succeeded()) {
                                attributed.complete();
                            } else {
                                attributed.fail(handlerAttribute.cause().getMessage());
                            }
                        });
                    }
                     */


                       /*
                    ticket.getPjs().forEach(pj -> {
                        sendTicketGlpi(xmlPj(id, (JsonObject) pj), handlerPj -> {
                            if (handlerPj.failed()) {
                                handler.handle(Future.failedFuture(handlerPj.cause().getMessage()));
                            }
                        });
                    });*/

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
                    futures.forEach(future -> {
                        if (future.succeeded() && future.result() != null) {
                            pivotTickets.add((PivotTicket) future.result());
                        }
                    });
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


                    for (int j = 0; j < fields.getLength(); j++) {
                        String name = (String) path.compile("name").evaluate(fields.item(j), XPathConstants.STRING);
                        Node value = (Node) path.compile("value").evaluate(fields.item(j), XPathConstants.NODE);

                        MapGlpiTicketFieldIntoPivotTicket(name, value, pivotTicket, id);
                    }

                    handler.handle(Future.succeededFuture(pivotTicket));

                } catch (XPathExpressionException e) {
                    handler.handle(Future.failedFuture(e.getMessage()));
                }
            } else {
                handler.handle(Future.failedFuture(resultTicket.cause().getMessage()));
            }
        });
    }

    private void MapGlpiTicketFieldIntoPivotTicket(String name, Node value, PivotTicket pivotTicket, String ticketId) {
        String stringValue = value.getTextContent();
        try {
            switch (name) {
                case GlpiConstants.ID_FIELD:
                    pivotTicket.setGlpiId(stringValue);
                    pivotTicket.setIwsId(stringValue);
                    break;
                case GlpiConstants.ID_ENT_CREATE_RESPONSE:
                    pivotTicket.setId(stringValue);
                    break;
                case GlpiConstants.ID_JIRA_CREATE_RESPONSE:
                    pivotTicket.setJiraId(stringValue);
                    break;
                case GlpiConstants.TITLE_FIELD:
                    pivotTicket.setTitle(stringValue);
                    break;
                case GlpiConstants.TICKETTYPE_FIELD:
                    stringValue = mapGlpiTypeValueToPivot(stringValue);
                    pivotTicket.setType(stringValue);
                    break;
                case GlpiConstants.STATUS_FIELD:
                    stringValue = mapGlpiStatusValueToPivot(stringValue);
                    pivotTicket.setStatus(stringValue, PivotConstants.ATTRIBUTION_GLPI);
                    break;
                case GlpiConstants.PRIORITY_FIELD:
                    stringValue = mapGlpiPriorityValueToPivot(stringValue);
                    pivotTicket.setPriority(stringValue);
                    break;
                case GlpiConstants.MODULES_FIELD:
                    stringValue = mapGlpiModuleValueToPivot(stringValue.trim());
                    pivotTicket.setCategorie(stringValue);
                    break;
                case GlpiConstants.DESCRIPTION_FIELD:
                    pivotTicket.setContent(stringValue);
                    break;
                case GlpiConstants.USERS_FIELD:
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
                                        if (requesterFieldValue.item(0).getTextContent().trim().equals(GlpiConstants.JIRA_ASSIGNED_VALUE)) {
                                            pivotTicket.setAttributed();
                                        }
                                        break;
                                }
                            }
                        }
                    }
                    break;
                case GlpiConstants.COMM_FIELD:
                    pivotTicket.setComments(convertGlpiCommentsToPivotComments(value));
                    break;
                case GlpiConstants.ATTACHMENT_FIELD:
                    addGlpiAttachmentsToPivot(value, pivotTicket, ticketId);
                    break;
                case GlpiConstants.DATE_CREA_FIELD:
                    pivotTicket.setGlpiCreatedAt(stringValue);
                    break;
                case GlpiConstants.DATE_UPDATE_FIELD:
                    pivotTicket.setGlpiUpdatedAt(stringValue);
                    break;
                case GlpiConstants.DATE_RESO_FIELD:
                    pivotTicket.setGlpiSolvedAt(stringValue);
                    break;
                default:
            }
        } catch (XPathException e) {
            log.info("Impossible to map field " + name + " for glpi ticket "  +  ticketId,e);
        }
    }



    private String mapGlpiModuleValueToPivot(String pivotValue) {
        /*if (GlpiConstants.MODULE_NAME.contains(pivotValue)) {
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
                        comment.put(PivotConstants.COMM_GLPI_ID_FIELD, contentValue.trim());
                        break;
                    case GlpiConstants.COMM_USER_NAME_FIELD:
                        comment.put(PivotConstants.COMM_USER_NAME_FIELD, contentValue.trim());
                        break;
                    case GlpiConstants.COMM_CONTENT_FIELD:
                        comment.put(PivotConstants.COMM_CONTENT_FIELD, contentValue.trim());
                        break;
                    case GlpiConstants.DATE_CREA_FIELD:
                        comment.put(PivotConstants.DATE_CREAGLPI_FIELD, contentValue.trim());
                        break;
                }
            }

            if (comment.getString(PivotConstants.COMM_CONTENT_FIELD).matches("^.*\\|.*\\|.*\\|.*$")) {
                listComments.add(comment.getString(PivotConstants.COMM_CONTENT_FIELD));
            }else{
                String formattedComment = PREFIX_GLPICOMMENTID

                        + comment.getString(PivotConstants.COMM_GLPI_ID_FIELD) + "|"
                        +  comment.getString(PivotConstants.COMM_USER_NAME_FIELD) + "|"
                        +   comment.getString(PivotConstants.DATE_CREAGLPI_FIELD) + "|"
                        + comment.getString(PivotConstants.COMM_CONTENT_FIELD);
                
                listComments.add(formattedComment);
            }

        }
        return listComments;
    }


    //TODO
    private void addGlpiAttachmentsToPivot(Node attachments, PivotTicket pivotTicket, String ticketId) throws XPathExpressionException {
        NodeList listAttachments = (NodeList) path.compile("array/data/value/struct").evaluate(attachments, XPathConstants.NODESET);
        List<Future> futures = new ArrayList<>();
        for (int i = 0; i < listAttachments.getLength(); i++) {
            Future<Boolean> future = Future.future();
            futures.add(future);
            NodeList attachmentFields = (NodeList) path.compile("member").evaluate(listAttachments.item(i), XPathConstants.NODESET);
            findFieldValue(attachmentFields, GlpiConstants.ATTACHMENT_ID_FIELD, resultId -> {
                if (resultId.succeeded()) {
                    if (!pluck(pivotTicket.getPjs(), PivotConstants.ATTACHMENT_GLPI_ID_FIELD).contains(resultId.result())) {
                        sendQueryToGlpi(GlpiEndpointHelper.generateXMLRPCGetDocumentQuery(ticketId, resultId.result()), resultAttachment -> {
                            if (resultAttachment.succeeded()) {
                                try {
                                    NodeList fields = (NodeList) path.compile("//methodResponse/params/param/value/struct/member")
                                            .evaluate(resultAttachment.result(), XPathConstants.NODESET);
                                    JsonObject attachment = new JsonObject();
                                    for (int j = 0; j < fields.getLength(); j++) {
                                        NodeList attributionNameField = (NodeList) path.compile("name").evaluate(fields.item(j), XPathConstants.NODESET);
                                        NodeList attributionValueField = (NodeList) path.compile("value").evaluate(fields.item(j), XPathConstants.NODESET);
                                        String contentValue = attributionValueField.item(0).getTextContent().trim();
                                        switch (attributionNameField.item(0).getTextContent()) {
                                            case GlpiConstants.ATTACHMENT_ID_FIELD:
                                                attachment.put(PivotConstants.ATTACHMENT_GLPI_ID_FIELD, contentValue);
                                                break;
                                            case GlpiConstants.ATTACHMENT_NAME_FORM:
                                                attachment.put(PivotConstants.ATTACHMENT_NAME_FIELD, contentValue);
                                                break;
                                            case GlpiConstants.ATTACHMENT_B64_FORM:
                                                attachment.put(PivotConstants.ATTACHMENT_CONTENT_FIELD, contentValue);
                                                break;
                                            case GlpiConstants.ATTACHMENT_TYPE_FIELD:
                                                attachment.put(PivotConstants.ATTACHMENT_TYPE_FIELD, contentValue);
                                                break;
                                        }
                                    }
                                    attachment.put(PivotConstants.ATTACHMENT_ENCODING_FIELD, PivotConstants.ATTACHMENT_ENCODING_BASE64);
                                    pivotTicket.addPj(attachment);
                                    future.complete(true);
                                } catch (XPathExpressionException e) {
                                    future.fail("An error occurred while parsing attachment: " + e.getMessage());
                                }
                            } else {
                                future.fail("Error while getting attachment: " + resultAttachment.cause().getMessage());
                            }
                        });
                    } else {
                        future.complete(false);
                    }
                } else {
                    future.fail(GlpiConstants.ATTACHMENT_ID_FIELD + " not found.");
                }
            });
        }
        CompositeFuture.join(futures).setHandler(event -> {
            if (event.failed()) {
                log.error("An error occured while getting attachments: " + event.cause().getMessage());
            }
        });
    }

    private ArrayList pluck(JsonArray array, String attribute) {
        ArrayList result = new ArrayList();
        array.forEach(item -> {
            result.add(attribute);
        });
        return result;
    }

    private void findFieldValue(NodeList fields, String fieldName, Handler<AsyncResult<String>> handler) {
        try {
            for (int i = 0; i < fields.getLength(); i++) {
                NodeList name = (NodeList) path.compile("name").evaluate(fields.item(i), XPathConstants.NODESET);
                NodeList value = (NodeList) path.compile("value").evaluate(fields.item(i), XPathConstants.NODESET);
                if (name.item(0).getTextContent().equals(fieldName)) {
                    handler.handle(Future.succeededFuture(value.item(0).getTextContent().trim()));
                    return;
                }
            }
            handler.handle(Future.failedFuture("Field not found"));
        } catch (XPathExpressionException e) {
            handler.handle(Future.failedFuture(e.getMessage()));
        }
    }
}
