package fr.openent.supportpivot.model.endpoint;

import fr.openent.supportpivot.constants.GlpiConstants;
import fr.openent.supportpivot.constants.PivotConstants;
import fr.openent.supportpivot.helpers.ParserTool;
import fr.openent.supportpivot.helpers.PivotHttpClient;
import fr.openent.supportpivot.helpers.PivotHttpClientRequest;
import fr.openent.supportpivot.managers.ConfigManager;
import fr.openent.supportpivot.model.ticket.PivotTicket;
import fr.openent.supportpivot.services.HttpClientService;
import fr.openent.supportpivot.helpers.LoginTool;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.xpath.*;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


class GlpiEndpoint implements Endpoint {


    //    private HttpClientService httpClientService;
    private PivotHttpClient httpClient;
    private XPath path;
    private String token;
    private Integer loginCheckCounter;

    private static final Logger log = LoggerFactory.getLogger(GlpiEndpoint.class);

    GlpiEndpoint(HttpClientService httpClientService) {
        this.loginCheckCounter = 0;
        XPathFactory xpf = XPathFactory.newInstance();
        this.path = xpf.newXPath();
        this.token = "";

        try {
            this.httpClient = httpClientService.getHttpClient(ConfigManager.getInstance().getGlpiHost());
        } catch (URISyntaxException e) {
            log.error("invalid uri " + e);
        }
    }

    @Override
    public void trigger(JsonObject data, Handler<AsyncResult<List<PivotTicket>>> handler) {
        //les updates qui nest pas le ticjket complet + recupere le ticket complet et envoyer les infos
        //il faut appeler la methode glpi.doLogin
        // avec en parametres login et password

        //recuperer la valeur de la session

        //il faut ensuite appeler la methode glpi.getTicket
        // avec en parametres la session, l'id ticket (et le id2name pour permettre à id de nommer la traduction des champs)

        //recupere le ticket avec toutes les infos
        this.getGlpiTickets(result -> {
            if (result.succeeded()) {
                Document xmlTickets = result.result();
                mapXmlGlpiToJsonPivot(xmlTickets, resultJson -> {
                    if (resultJson.succeeded()) {
                        handler.handle(Future.succeededFuture(resultJson.result()));
                    }
                });
            } else {
                handler.handle(Future.failedFuture(result.cause().getMessage()));
            }
        });
        // TODO
        // jsonTickets = service.GetTickets
        // jsonTickets.foreach(ticket =>)
        // Vérification après cron => GetTicket, etc
        // return list tickets


    }

    @Override
    public void process(JsonObject ticketData, Handler<AsyncResult<PivotTicket>> handler) {
        //creer le ticket a partir du json object
        //creer le ticket

        // TODO ici: error

        // (Exclusivement  ENT): modif/Creation => envoi un ticket => traiter


    }

    @Override
    public void send(PivotTicket ticket, Handler<AsyncResult<PivotTicket>> handler) {
        // TODO get ticket from GLPI and add an "if ticket exist" => update else => create
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

        // TODO VERIFIER L'EXISTENCE
        // Après trigger ou process d'un autre endpoint
        // en fonction Créer ou Vérifier les données de Mise à jour
        // convertir en GLPI

    }

    /**
     * Send a request to GLPI:
     * - add the current token to the current request
     * - if request login failed, re-login, and resend the same request (with new token)
     * - handle the xml body of the response.
     *
     * @param method  Used to send request ("POST", "GET", ...)
     * @param uri     Called to GLPI
     * @param xmlData Passed to the request
     * @param handler To return result of the request
     */
    private void sendRequest(String method, String uri, Document xmlData, Handler<AsyncResult<Document>> handler) {
        this.setXmlToken(xmlData);

        PivotHttpClientRequest sendingRequest = this.httpClient.createRequest(method, uri, ParserTool.getStringFromDocument(xmlData));
        this.setHeaderRequest(sendingRequest);

        sendingRequest.startRequest(result -> {
            if (result.succeeded()) {
                result.result().bodyHandler(body -> {
                    Document xml = ParserTool.getParsedXml(body);
                    this.noReloginCheck(xml, loginResult -> {
                        if (loginResult.succeeded()) {
                            if (loginResult.result()) {
                                handler.handle(Future.succeededFuture(xml));
                            } else {
                                this.sendRequest(method, uri, xmlData, requestResult -> {
                                    if (requestResult.succeeded()) {
                                        handler.handle(Future.succeededFuture(requestResult.result()));
                                    } else {
                                        handler.handle(Future.failedFuture(
                                                "Error at re-sending request after re-authentication: "
                                                        + requestResult.cause().getMessage())
                                        );
                                    }
                                });
                            }
                        } else {
                            handler.handle(Future.failedFuture("Authentication problem: " + loginResult.cause().getMessage()));
                        }
                    });
                });
            } else {
                handler.handle(Future.failedFuture("Sending request failed: " + result.cause().getMessage()));
            }
        });

    }

    /**
     * Set session parameter of the given xml request with the current token.
     *
     * @param xml which will get the new session parameter set with the current token.
     */
    private void setXmlToken(Document xml) {
        try {
            String expression = "//methodCall/params/param/value/struct/member";
            NodeList fields = (NodeList) path.evaluate(expression, xml, XPathConstants.NODESET);

            for (int i = 0; i < fields.getLength(); i++) {
                NodeList name = (NodeList) path.compile("name").evaluate(fields.item(i), XPathConstants.NODESET);
                if (name.item(0).getTextContent().equals("session")) {
                    NodeList value = (NodeList) path.compile("value").evaluate(fields.item(i), XPathConstants.NODESET);
                    value.item(0).setNodeValue(this.token);
                    return;
                }
            }

            expression = "//methodCall/params/param/value/struct";
            Node struct = ((NodeList) path.evaluate(expression, xml, XPathConstants.NODESET)).item(0);

            Element member = xml.createElement("member");
            Element name = xml.createElement("name");
            Element value = xml.createElement("value");
            Element stringValue = xml.createElement("string");

            name.setTextContent("session");
            stringValue.setTextContent(this.token);

            value.appendChild(stringValue);
            member.appendChild(name);
            member.appendChild(value);

            struct.appendChild(member);

            /*Element structElem = (Element) struct.item(0);
            Node importNode = structElem.importNode(member, true); //TODO find a way to import member document, into struct one.*/

        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }

    }

    private void createGlpiTicket(PivotTicket ticket, Handler<AsyncResult<PivotTicket>> handler) {
        Document xmlTicket = xmlFormTicket(ticket);
        this.sendRequest("POST", ConfigManager.getInstance().getGlpiRootUri(), xmlTicket, result -> {
            if (result.succeeded()) {
                Document xml = result.result();
                this.getIdFromGlpiTicket(xml, handlerId -> {

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
        this.sendRequest("POST", ConfigManager.getInstance().getGlpiRootUri(), xml, result -> {
            if (result.succeeded()) {
                handler.handle(Future.succeededFuture());
                // TODO check if request well passed (this means that we've no "faultcode" in response.
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

    private void setHeaderRequest(PivotHttpClientRequest request) {
        request.getHttpClientRequest().putHeader("Content-Type", "text/xml");
    }

    private void getGlpiTickets(Handler<AsyncResult<Document>> handler) {
        try {
            String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><methodCall><methodName>glpi.listTickets</methodName><params><param><value><struct>" +
                    "<member><name>id2name</name><value><string></string></value></member>" +
                    GlpiConstants.END_XML_FORMAT;


            this.sendRequest("POST", ConfigManager.getInstance().getGlpiRootUri(), ParserTool.getParsedXml(xml), result -> {
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
