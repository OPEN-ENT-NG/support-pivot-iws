package fr.openent.supportpivot.model.endpoint;

import fr.openent.supportpivot.constants.GlpiConstants;
import fr.openent.supportpivot.constants.PivotConstants;
import fr.openent.supportpivot.helpers.ParserTool;
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

    private GlpiService glpiService;
    private XPath path;

    private static final Logger log = LoggerFactory.getLogger(GlpiEndpoint.class);


    GlpiEndpoint(GlpiService glpiService) {
        this.glpiService = glpiService;
        XPathFactory xpf = XPathFactory.newInstance();
        this.path = xpf.newXPath();
    }

    @Override
    public void trigger(JsonObject data, Handler<AsyncResult<List<PivotTicket>>> handler) {
        //recupere le ticket avec toutes les infos
        this.getGlpiTickets(result -> {
            if (result.succeeded()) {
                Document xmlTickets = result.result();
                mapXmlGlpiToJsonPivot(xmlTickets, resultJson -> {
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
    }

    @Override
    public void send(PivotTicket ticket, Handler<AsyncResult<PivotTicket>> handler) {
        if (ticket.getGlpiId() == null) {
            this.createGlpiTicket(ticket, result -> {
                if (result.succeeded()) {
                    handler.handle(Future.succeededFuture(result.result()));
                } else {
                    handler.handle(Future.failedFuture(result.cause().getMessage()));
                }
            });

        } else {
            this.updateGlpiTicket(ticket, result -> {
                if (result.succeeded()) {
                    handler.handle(Future.succeededFuture(result.result()));
                } else {
                    handler.handle(Future.failedFuture(result.cause().getMessage()));
                }
            });
        }
    }

    private void createGlpiTicket(PivotTicket ticket, Handler<AsyncResult<PivotTicket>> handler) {
        Document xmlTicket = xmlFormTicket(ticket, "glpi.createTicket");
        this.glpiService.sendRequest("POST", ConfigManager.getInstance().getGlpiRootUri(), xmlTicket, result -> {
            if (result.succeeded()) {
                Document xml = result.result();
                this.glpiService.getIdFromGlpiTicket(xml, handlerId -> {

                    String id = handlerId.result().trim();

                    // Une fois l'id récupérer, on peut notifie r à l'ENT que la communication du ticket a fonctionné
                    ticket.setGlpiId(id);
                    ticket.setIwsId(ticket.getId());
                    handler.handle(Future.succeededFuture(ticket));

                    // traitement des commentaires
                    Document xmlCommentTicket = this.generateXmlFirstComment(id, ticket);

                    this.sendTicketGlpi(xmlCommentTicket, handlerFirstComment -> {
                        if (handlerFirstComment.succeeded()) {

                            ticket.getComments().forEach(comment -> {
                                this.sendTicketGlpi(this.xmlComments(id, (String) comment), handlerComment -> {
                                    if (handlerComment.failed()) {
                                        handler.handle(Future.failedFuture(handlerComment.cause().getMessage()));
                                    }
                                });
                            });

                            ticket.getPjs().forEach(pj -> {
                                this.sendTicketGlpi(this.xmlPj(id, (JsonObject) pj), handlerPj -> {
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

    private void updateGlpiTicket(PivotTicket ticket, Handler<AsyncResult<PivotTicket>> handler) {
        log.info("TODO: add update process");
    }

    private void sendTicketGlpi(Document xml, Handler<AsyncResult<JsonObject>> handler) {
        this.glpiService.sendRequest("POST", ConfigManager.getInstance().getGlpiRootUri(), xml, result -> {
            if (result.succeeded()) {
                handler.handle(Future.succeededFuture());
            } else {
                handler.handle(Future.failedFuture(result.cause()));
            }
        });
    }

    private Document xmlComments(String id, String comment) {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><methodCall><methodName>glpi.addTicketFollowup</methodName><params><param><value><struct>";

        xml += this.xmlAddField(GlpiConstants.TICKET_ID_FORM, "string", id);

        xml += this.xmlAddField(GlpiConstants.CONTENT_COMMENT, "string", comment);

        xml += GlpiConstants.END_XML_FORMAT;
        return ParserTool.getParsedXml(xml);
    }

    private Document xmlPj(String id, JsonObject pj) {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><methodCall><methodName>glpi.addTicketDocument</methodName><params><param><value><struct>";
        xml += this.xmlAddField(GlpiConstants.TICKET_ID_FORM, "string", id);
        xml += this.xmlAddField(GlpiConstants.ATTACHMENT_NAME_FORM, "string", pj.getString("nom"));
        xml += this.xmlAddField(GlpiConstants.ATTACHMENT_B64_FORM, "string", pj.getString("contenu"));

        xml += GlpiConstants.END_XML_FORMAT;
        return ParserTool.getParsedXml(xml);
    }

    private Document generateXmlFirstComment(String Id, PivotTicket ticket) {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><methodCall><methodName>glpi.addTicketFollowup</methodName><params><param><value><struct>";

        xml += this.xmlAddField(GlpiConstants.TICKET_ID_FORM, "string", Id);

        String content = "id_ent: " + ticket.getId() + "| status_ent: " + ticket.getStatus() + " | date de creation: " + ticket.getCreatedAt() + "\n" + "demandeur: " + ticket.getUsers();
        xml += this.xmlAddField(GlpiConstants.CONTENT_COMMENT, "string", content);

        xml += GlpiConstants.END_XML_FORMAT;
        return ParserTool.getParsedXml(xml);
    }

    private Document xmlFormTicket(PivotTicket ticket) {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><methodCall><methodName>glpi.createTicket</methodName><params><param><value><struct>";
        xml += this.xmlAddField(GlpiConstants.TITLE_CREATE, "string", ticket.getTitle());
        xml += this.xmlAddField(GlpiConstants.DESCRIPTION_CREATE, "string", ticket.getContent());
        xml += this.xmlAddField(GlpiConstants.ENTITY_CREATE, "integer", GlpiConstants.ENTITY_ID);
        xml += this.xmlAddField(GlpiConstants.CATEGORY_CREATE, "string", ConfigManager.getInstance().getGlpiCategory());
        xml += this.xmlAddField(GlpiConstants.REQUESTER_CREATE, "integer", GlpiConstants.REQUESTER_ID);
        xml += this.xmlAddField(GlpiConstants.TYPE_CREATE, "integer", GlpiConstants.TYPE_ID);

        xml += GlpiConstants.END_XML_FORMAT;
        return ParserTool.getParsedXml(xml);
    }

    private String xmlAddField(String fieldName, String valueType, String value) {
        return "<member><name>" + fieldName + "</name><value><" + valueType + ">" + value + "</" + valueType + "></value></member>";
    }

    private void getGlpiTickets(Handler<AsyncResult<Document>> handler) {
        try {
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><methodCall><methodName>glpi.listTickets</methodName><params><param><value><struct>" +
                    "<member><name>id2name</name><value><string></string></value></member>" +
                    GlpiConstants.END_XML_FORMAT;


            this.glpiService.sendRequest("POST", ConfigManager.getInstance().getGlpiRootUri(), ParserTool.getParsedXml(xml), result -> {
                if (result.succeeded()) {
                    handler.handle(Future.succeededFuture(result.result()));
                } else {
                    handler.handle(Future.failedFuture(result.cause().getMessage()));
                }
            });
        } catch (Exception e) {
            handler.handle(Future.failedFuture(e));
        }
    }

    private void mapXmlGlpiToJsonPivot(Document xmlTickets, Handler<AsyncResult<List<PivotTicket>>> handler) {
        try {
            String expression = "//methodResponse/params/param/value/array/data/value/struct";
            NodeList tickets = (NodeList) this.path.evaluate(expression, xmlTickets, XPathConstants.NODESET);
            List<PivotTicket> pivotTickets = new ArrayList<>();

            List<Future> futures = new ArrayList<>();
            for (int i = 0; i < tickets.getLength(); i++) {
                Future<PivotTicket> future = Future.future();
                futures.add(future);
                expression = "member";
                Node ticket = tickets.item(i);
                NodeList fields = (NodeList) this.path.evaluate(expression, ticket, XPathConstants.NODESET);
                this.findFieldValue(fields, "is_deleted", resultValue -> {
                    if (resultValue.succeeded()) {
                        if (resultValue.result().trim().equals("0")) {
                            this.mapXmlTicketToJsonPivot(ticket, result -> {
                                if (result.succeeded()) {
                                    future.complete(result.result());
                                } else {
                                    String message = "A ticket can not be parsed: " + result.cause().getMessage();
                                    log.info(message);
                                    future.fail(message);
                                }
                            });
                        } else {
                            future.complete();
                        }
                    } else {
                        future.fail("is_deleted field can not be found.");
                    }
                });
            }
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

    private void mapXmlTicketToJsonPivot(Node nodeTicket, Handler<AsyncResult<PivotTicket>> handler) {
        PivotTicket pivotTicket = new PivotTicket();

        this.glpiService.getIdFromGlpiTicket(nodeTicket, result -> {
            if (result.succeeded()) {
                String id = result.result().trim();
                this.glpiService.getTicket(id, resultTicket -> {
                    if (resultTicket.succeeded()) {
                        XPathExpression expr = null;
                        try {
                            expr = this.path.compile("//methodResponse/params/param/value/struct/member");
                            NodeList fields = (NodeList) expr.evaluate(resultTicket.result(), XPathConstants.NODESET);

                            List<Future> futures = new ArrayList<>();
                            for (int j = 0; j < fields.getLength(); j++) {
                                NodeList name = (NodeList) this.path.compile("name").evaluate(fields.item(j), XPathConstants.NODESET);
                                NodeList value = (NodeList) this.path.compile("value").evaluate(fields.item(j), XPathConstants.NODESET);

                                Future<Boolean> future = Future.future();
                                futures.add(future);
                                this.parseGlpiFieldToPivot(name.item(0).getTextContent(), value, pivotTicket, id, resultField -> {
                                    if (resultField.succeeded()) {
                                        if (resultField.result()) {
                                            future.complete(resultField.result());
                                        } else {
                                            future.fail("Problem while parsing a ticket");
                                        }
                                    } else {
                                        future.fail(resultField.cause().getMessage());
                                    }
                                });
                            }

                            CompositeFuture.any(futures).setHandler(event -> {
                                if (event.succeeded()) {
                                    handler.handle(Future.succeededFuture(pivotTicket));
                                } else {
                                    handler.handle(Future.failedFuture(event.cause().getMessage()));
                                }
                            });
                        } catch (XPathExpressionException e) {
                            handler.handle(Future.failedFuture(e.getMessage()));
                        }
                    } else {
                        handler.handle(Future.failedFuture(resultTicket.cause().getMessage()));
                    }
                });
            } else {
                handler.handle(Future.failedFuture(result.cause().getMessage()));
            }
        });
    }

    private void parseGlpiFieldToPivot(String name, NodeList value, PivotTicket pivotTicket, String ticketId, Handler<AsyncResult<Boolean>> handler) {
        String pivotValue = value.item(0).getTextContent();
        try {
            switch (name) {
                case GlpiConstants.ID_FIELD:
                    pivotTicket.setGlpiId(pivotValue);
                    break;
                case GlpiConstants.TITLE_FIELD:
                    pivotTicket.setTitle(pivotValue);
                    break;
                case GlpiConstants.TICKETTYPE_FIELD:
                    pivotValue = mapGlpiTypeValueToPivot(pivotValue);
                    pivotTicket.setType(pivotValue);
                    break;
                case GlpiConstants.STATUS_FIELD:
                    pivotValue = mapGlpiStatusValueToPivot(pivotValue);
                    pivotTicket.setStatus(pivotValue, PivotConstants.ATTRIBUTION_GLPI);
                    break;
                case GlpiConstants.PRIORITY_FIELD:
                    pivotValue = mapGlpiPriorityValueToPivot(pivotValue);
                    pivotTicket.setPriority(pivotValue);
                    break;
                case GlpiConstants.MODULES_FIELD:
                    pivotValue = mapGlpiModuleValueToPivot(pivotValue.trim());
                    pivotTicket.setCategorie(pivotValue);
                    break;
                case GlpiConstants.DESCRIPTION_FIELD:
                    pivotTicket.setContent(pivotValue);
                    break;
                case GlpiConstants.USERS_FIELD:
                    XPathExpression expr = path.compile("struct/member");
                    NodeList userTypes = (NodeList) expr.evaluate(value.item(0), XPathConstants.NODESET);
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
                    this.addGlpiCommentsToPivot(value.item(0), pivotTicket);
                    break;
                case GlpiConstants.ATTACHMENT_FIELD:
                    this.addGlpiAttachmentsToPivot(value.item(0), pivotTicket, ticketId);
                    break;
                case GlpiConstants.DATE_CREA_FIELD:
                    pivotTicket.setGlpiCreatedAt(pivotValue);
                    break;
                case GlpiConstants.DATE_UPDATE_FIELD:
                    pivotTicket.setGlpiUpdatedAt(pivotValue);
                    break;
                case GlpiConstants.DATE_RESO_FIELD:
                    pivotTicket.setGlpiSolvedAt(pivotValue);
                    break;
                default:
                    handler.handle(Future.succeededFuture(false));
                    return;
            }
            handler.handle(Future.succeededFuture(true));
        } catch (Exception e) {
            handler.handle(Future.failedFuture("An error occurred while parsing a field " + name + ": " + e.getMessage()));
        }
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

    private void addGlpiCommentsToPivot(Node followups, PivotTicket pivotTicket) throws XPathExpressionException {
        NodeList comments = (NodeList) path.compile("array/data/value/struct").evaluate(followups, XPathConstants.NODESET);
        JsonObject comment = new JsonObject();
        JsonArray listComments = new JsonArray();
        for (int i = 0; i < comments.getLength(); i++) {
            NodeList commentFields = (NodeList) path.compile("member").evaluate(comments.item(i), XPathConstants.NODESET);
            for (int j = 0; j < commentFields.getLength(); j++) {
                NodeList commFieldName = (NodeList) path.compile("name").evaluate(commentFields.item(j), XPathConstants.NODESET);
                NodeList commFieldValue = (NodeList) path.compile("value").evaluate(commentFields.item(j), XPathConstants.NODESET);
                String contentValue = commFieldValue.item(0).getTextContent();
                switch (commFieldName.item(0).getTextContent()) {
                    case GlpiConstants.COMM_ID_FIELD:
                        comment.put(PivotConstants.COMM_GLPI_ID_FIELD, contentValue.trim());
                        break;
                    case GlpiConstants.COMM_USER_NAME_FIELD:
                        comment.put(PivotConstants.COMM_USER_NAME_FIELD, contentValue.trim());
                        break;
                    case GlpiConstants.COMM_CONTENT_FIELD:
                        comment.put(PivotConstants.COMM_CONTENT_FIELD, contentValue.trim());
                        break;
                }
            }
            if (!this.pluck(pivotTicket.getComments(), PivotConstants.COMM_GLPI_ID_FIELD).contains(comment.getString(PivotConstants.COMM_GLPI_ID_FIELD))) {
                listComments.add(comment);
            }
        }
        pivotTicket.setComments(listComments);
    }

    private void addGlpiAttachmentsToPivot(Node attachments, PivotTicket pivotTicket, String ticketId) throws XPathExpressionException {
        NodeList listAttachments = (NodeList) path.compile("array/data/value/struct").evaluate(attachments, XPathConstants.NODESET);
        List<Future> futures = new ArrayList<>();
        for (int i = 0; i < listAttachments.getLength(); i++) {
            Future<Boolean> future = Future.future();
            futures.add(future);
            NodeList attachmentFields = (NodeList) path.compile("member").evaluate(listAttachments.item(i), XPathConstants.NODESET);
            this.findFieldValue(attachmentFields, GlpiConstants.ATTACHMENT_ID_FIELD, resultId -> {
                if (resultId.succeeded()) {
                    if (!this.pluck(pivotTicket.getPjs(), PivotConstants.ATTACHMENT_GLPI_ID_FIELD).contains(resultId.result())) {
                        this.getAttachment(ticketId, resultId.result(), resultAttachment -> {
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

    private void getAttachment(String glpiId, String documentId, Handler<AsyncResult<Document>> handler) {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><methodCall><methodName>glpi.getDocument</methodName><params><param><value><struct>" +
                "<member><name>id2name</name><value><string></string></value></member>" +
                "<member><name>ticket</name><value><string>" + glpiId + "</string></value></member>" +
                "<member><name>document</name><value><string>" + documentId + "</string></value></member>" +
                GlpiConstants.END_XML_FORMAT;


        this.glpiService.sendRequest("POST", ConfigManager.getInstance().getGlpiRootUri(), ParserTool.getParsedXml(xml), result -> {
            if (result.succeeded()) {
                handler.handle(Future.succeededFuture(result.result()));
            } else {
                handler.handle(Future.failedFuture(result.cause().getMessage()));
            }
        });
    }
}
