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

    /**
     * Thanks the  xmlResult of a request, get a new token from GLPI if needed.
     * Then the handler return if the token has not been reloaded
     * and if it has been, the request, the request must be rerun
     *
     * @param xmlResult From the current sent request
     * @param handler   return if the token has not been reloaded or otherwise, the request must be rerun
     */
    private void noReloginCheck(Document xmlResult, Handler<AsyncResult<Boolean>> handler) {
        if (this.loginCheckCounter == 3) {
            handler.handle(Future.failedFuture("Login maximum attempt reached"));
            return;
        }

        NodeList nodeList = xmlResult.getElementsByTagName("member");
        for (int i = 0; i < nodeList.getLength(); i++) {
            Element session = (Element) nodeList.item(i);
            String fieldName = session.getElementsByTagName("name").item(0).getTextContent();
            if (fieldName.equals(GlpiConstants.ERROR_CODE_NAME)) {
                String fieldValue = session.getElementsByTagName("value").item(0).getTextContent().trim();
                if (fieldValue.equals(GlpiConstants.ERROR_LOGIN_CODE)) {
                    this.loginCheckCounter++;
                    LoginTool.getGlpiSessionToken(this.httpClient, result -> {
                        if (result.succeeded()) {
                            this.token = result.result().trim();
                            this.loginCheckCounter = 0;
                            handler.handle(Future.succeededFuture(false));
                        } else {
                            this.noReloginCheck(xmlResult, loginResult -> {
                                if (loginResult.succeeded()) {
                                    handler.handle(Future.succeededFuture(loginResult.result()));
                                } else {
                                    handler.handle(Future.failedFuture(loginResult.cause().getMessage()));
                                }
                            });
                        }
                    });
                } else {
                    handler.handle(Future.failedFuture("An error occurred at login to GLPI: " + xmlResult.toString()));
                }
                return;
            }
        }
        handler.handle(Future.succeededFuture(true));
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

            NodeList tickets = (NodeList) path.evaluate(expression, xmlTickets, XPathConstants.NODESET);
            List<PivotTicket> pivotTickets = new ArrayList<>();

            for (int i = 0; i < tickets.getLength(); i++) {
                PivotTicket pivotTicket = new PivotTicket();
                Node ticket = tickets.item(i);
                XPathExpression expr = path.compile("member");
                NodeList fields = (NodeList) expr.evaluate(ticket, XPathConstants.NODESET);

                for (int j = 0; j < fields.getLength(); j++) {
                    NodeList name = (NodeList) path.compile("name").evaluate(fields.item(j), XPathConstants.NODESET);
                    NodeList value = (NodeList) path.compile("value").evaluate(fields.item(j), XPathConstants.NODESET);
                    this.parseGlpiFieldToPivot(name.item(0).getTextContent(), value, pivotTicket);
                }

                this.addComments(pivotTicket);

                pivotTickets.add(pivotTicket);
            }
            handler.handle(Future.succeededFuture(pivotTickets));
        } catch (Exception e) {
            handler.handle(Future.failedFuture(e));
        }
    }

    private void getIdFromGlpiTicket(Document xmlTicket, Handler<AsyncResult<String>> handler) {
        try {
            String expression = "//methodResponse/params/param/value/struct/member";

            NodeList fields = (NodeList) path.evaluate(expression, xmlTicket, XPathConstants.NODESET);

            for (int i = 0; i < fields.getLength(); i++) {
                NodeList name = (NodeList) path.compile("name").evaluate(fields.item(i), XPathConstants.NODESET);
                NodeList value = (NodeList) path.compile("value").evaluate(fields.item(i), XPathConstants.NODESET);

                String pivotValue = value.item(0).getTextContent();

                if (name.item(0).getTextContent().equals(GlpiConstants.ID_FIELD)) {
                    handler.handle(Future.succeededFuture(value.item(0).getTextContent()));
                    return;
                }
            }
            handler.handle(Future.failedFuture("Missing id."));
        } catch (Exception e) {
            handler.handle(Future.failedFuture(e));
        }
    }

    private void parseGlpiFieldToPivot(String name, NodeList value, PivotTicket pivotTicket) throws XPathExpressionException {
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
                            if (requesterFieldName.item(0).getTextContent().equals("name")) {
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

                            if (requesterFieldName.item(0).getTextContent().equals("name")) {
                                pivotTicket.setCreator(requesterFieldValue.item(0).getTextContent());
                            }
                        }
                    }
                    break;
                case GlpiConstants.DATE_CREA_FIELD:
                    try {
                        pivotTicket.setGlpiCreatedAt(pivotValue);
                    } catch (Exception e) {
                        log.error("Error at parsing date", (Object) e.getStackTrace());
                    }
                    break;
                case GlpiConstants.DATE_UPDATE_FIELD:
                    pivotTicket.setGlpiUpdatedAt(pivotValue);
                    break;
                case GlpiConstants.DATE_RESO_FIELD:
                    try {
                        pivotTicket.setGlpiSolvedAt(pivotValue);
                    } catch (Exception e) {
                        log.error("Error at parsing date", (Object) e.getStackTrace());
                    }
                    break;
            }
        } catch (Exception e) {
            log.error("An error occurred while parsing a field " + name + ": " + e.getMessage(), (Object) e.getStackTrace());
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

    private void addComments(PivotTicket pivotTicket) {
//        pivotTicket
    }
}
