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
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.xpath.*;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


class GlpiEndpoint implements Endpoint {


    //    private HttpClientService httpClientService;
    private PivotHttpClient httpClient;
    private XPath path;
    private String token;
    private ConfigManager config;

    private static final Logger log = LoggerFactory.getLogger(GlpiEndpoint.class);

    GlpiEndpoint(ConfigManager config, HttpClientService httpClientService) {
        this.config = config;
        XPathFactory xpf = XPathFactory.newInstance();
        this.path = xpf.newXPath();

        try {
            this.httpClient = httpClientService.getHttpClient(config.getGlpiHost());
        } catch (URISyntaxException e) {
            log.error("invalid uri " + e);
        }

        LoginTool.getGlpiSessionToken(config, this.httpClient, handler -> {
            if (handler.succeeded()) {
                this.token = handler.result();
            } else {
                log.error(handler.cause().getMessage(), (Object) handler.cause().getStackTrace());
            }
        });
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
        if (this.token != null) {

            getGlpiTickets(result -> {
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

        } else {
            log.error("Session error");
        }

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

    private void createGlpiTicket(PivotTicket ticket, Handler<AsyncResult<PivotTicket>> handler) {
        String xmlTicket = xmlFormTicket(ticket);
        PivotHttpClientRequest sendingRequest = this.httpClient.createPostRequest(
                this.config.getGlpiRootUri(), xmlTicket
        );

        this.setHeaderRequest(sendingRequest);
        sendingRequest.startRequest(result -> {
            if (result.succeeded()) {
                result.result().bodyHandler(body -> {
                    Document xml = ParserTool.getParsedXml(body);
                    this.getIdFromGlpiTicket(xml, handlerId -> {

                        String id = handlerId.result().trim();

                        // Une fois l'id récupérer, on peut notifier à l'ENT que la communication du ticket a fonctionné
                        ticket.setGlpiId(id);
                        handler.handle(Future.succeededFuture(ticket));

                        // traitement des commentaires
                        String xmlCommentTicket = this.generateXmlFirstComment(id, ticket);

                        this.sendCommentTicketGlpi(xmlCommentTicket, handlerFirstComment -> {
                            if (handlerFirstComment.succeeded()) {
                                ticket.getComments().forEach(comment -> {
                                    this.sendCommentTicketGlpi(this.xmlComments(id, (String) comment), handlerComment -> {
                                        if (handlerComment.failed()) {
                                            handler.handle(Future.failedFuture(handlerComment.cause().getMessage()));
                                        }
                                    });
                                });

                                // TODO PJ (when Ent dev is fixed)
                                        /*ticket.getPj().forEach(pj -> {
                                            log.info("Un pj: " + pj.toString());
                                            *//*this.sendPjTicketGlpi(this.xmlComments(id, (String) comment), handlerComment -> {
                                                if (handlerComment.failed()) {
                                                    handler.handle(Future.failedFuture(handlerComment.cause()));
                                                }
                                            });*//*
                                        });*/

                            } else {
                                handler.handle(Future.failedFuture(handlerFirstComment.cause().getMessage()));
                            }
                        });
                    });
                });
            } else {
                log.error("fail ");
                handler.handle(Future.failedFuture(result.cause()));
            }
        });
    }

    private void updateGlpiTicket(PivotTicket ticket, Handler<AsyncResult<PivotTicket>> handler) {
        log.info("TODO: add update process");
    }

    private void sendCommentTicketGlpi(String xmlComment, Handler<AsyncResult<JsonObject>> handler) {
        PivotHttpClientRequest sendingRequest = this.httpClient.createPostRequest(
                this.config.getGlpiRootUri(), xmlComment
        );
        this.setHeaderRequest(sendingRequest);

        sendingRequest.startRequest(result -> {
            if (result.succeeded()) {
                result.result().bodyHandler(body -> {
                    handler.handle(Future.succeededFuture());
                    // TODO check if request well passed
                });
            } else {
                handler.handle(Future.failedFuture(result.cause()));
            }
        });
    }

    private String xmlComments(String id, String comment) {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><methodCall><methodName>glpi.addTicketFollowup</methodName><params><param><value><struct>" +
                "<member><name>session</name><value><string>" + this.token + "</string></value></member>";

        xml += this.xmlAddField(xml, GlpiConstants.TICKET_ID_COMMENT, "string", id);

        xml += this.xmlAddField(xml, GlpiConstants.CONTENT_COMMENT, "string", comment);

        xml += GlpiConstants.END_XML_FORMAT;
        return xml;
    }

    private String generateXmlFirstComment(String Id, PivotTicket ticket) {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><methodCall><methodName>glpi.addTicketFollowup</methodName><params><param><value><struct>" +
                "<member><name>session</name><value><string>" + this.token + "</string></value></member>";

        xml += this.xmlAddField(xml, GlpiConstants.TICKET_ID_COMMENT, "string", Id);

        String content = "id_ent: " + ticket.getId().toString() + "| status_ent: " + ticket.getStatus() + " | date de creation: " + ticket.getCreatedAt() + "\n" + "demandeur: " + ticket.getUsers();
        xml += this.xmlAddField(xml, GlpiConstants.CONTENT_COMMENT, "string", content);

        xml += GlpiConstants.END_XML_FORMAT;
        return xml;
    }

    private String xmlFormTicket(PivotTicket ticket) {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><methodCall><methodName>glpi.createTicket</methodName><params><param><value><struct>" +
                "<member><name>session</name><value><string>" + this.token + "</string></value></member>";

        xml += this.xmlAddField(xml, GlpiConstants.TITLE_CREATE, "string", ticket.getTitle());
        xml += this.xmlAddField(xml, GlpiConstants.DESCRIPTION_CREATE, "string", ticket.getContent());
        xml += this.xmlAddField(xml, GlpiConstants.ENTITY_CREATE, "integer", GlpiConstants.ENTITY_ID);
        xml += this.xmlAddField(xml, GlpiConstants.CATEGORY_CREATE, "string", config.getGlpiCategory());
        xml += this.xmlAddField(xml, GlpiConstants.REQUESTER_CREATE, "integer", GlpiConstants.REQUESTER_ID);
        xml += this.xmlAddField(xml, GlpiConstants.TYPE_CREATE, "integer", GlpiConstants.TYPE_ID);
//      xml += this.xmlAddField(xml, GlpiConstants.LOCATION_CREATE, "integer", ticket.getRne());

        xml += GlpiConstants.END_XML_FORMAT;
        return xml;
    }

    private String xmlAddField(String xml, String fieldName, String valueType, String value) {
        return "<member><name>" + fieldName + "</name><value><" + valueType + ">" + value + "</" + valueType + "></value></member>";
    }

    private void setHeaderRequest(PivotHttpClientRequest request) {
        request.getHttpClientRequest().putHeader("Content-Type", "text/xml");
    }

    private void getGlpiTickets(Handler<AsyncResult<Document>> handler) {
        try {
            PivotHttpClientRequest sendingRequest = this.httpClient.createPostRequest(this.config.getGlpiRootUri(),
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?><methodCall><methodName>glpi.listTickets</methodName><params><param><value><struct>" +
                            "<member><name>session</name><value><string>" + this.token + "</string></value></member>" +
                            "<member><name>id2name</name><value><string></string></value></member>" +
                            GlpiConstants.END_XML_FORMAT);

            this.setHeaderRequest(sendingRequest);

            sendingRequest.startRequest(result -> {
                if (result.succeeded()) {
                    result.result().bodyHandler(body -> {
                        Document xml = ParserTool.getParsedXml(body);
                        handler.handle(Future.succeededFuture(xml));
                    });
                } else {
                    log.error("fail ");
                    handler.handle(Future.failedFuture(result.cause()));
//                        log.error(result.cause().getMessage());
                }
            });
        } catch (Exception e) {
            log.error("fail 2");
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
                case GlpiConstants.MODULES_FIELD: // TODO: find a way to map it (Ask the Boss master "Laurent")
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
                        if (typeName.equals(GlpiConstants.REQUESTER_FIELD)) {
                            for (int j = 0; j < userFields.getLength(); j++) {
                                NodeList requesterFieldName = (NodeList) path.compile("name").evaluate(userFields.item(j), XPathConstants.NODESET);
                                NodeList requesterFieldValue = (NodeList) path.compile("value").evaluate(userFields.item(j), XPathConstants.NODESET);
                                if (requesterFieldName.item(0).getTextContent().equals("name")) {
                                    pivotTicket.setCreator(requesterFieldValue.item(0).getTextContent());
                                }
                            }
                            break;
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

    private String searchValue(XPath npath, Node node) throws XPathExpressionException {
        Node n3 = null;
        String value = null;
        String expression = "//methodResponse/params/param/value/array/data/value/struct/member/name/value";
        npath.compile(expression);
        NodeList listValue = (NodeList) npath.evaluate(expression, node, XPathConstants.NODESET);
        int nodeLength3 = listValue.getLength();

        for (int i = 0; i < nodeLength3; i++) {
            n3 = listValue.item(i);
            value = n3.getTextContent();
        }

        return value;
    }
}
