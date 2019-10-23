package fr.openent.supportpivot.services;

import fr.openent.supportpivot.constants.GlpiConstants;
import fr.openent.supportpivot.helpers.LoginTool;
import fr.openent.supportpivot.helpers.ParserTool;
import fr.openent.supportpivot.helpers.PivotHttpClient;
import fr.openent.supportpivot.helpers.PivotHttpClientRequest;
import fr.openent.supportpivot.managers.ConfigManager;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.net.URISyntaxException;

public class GlpiService {

    private PivotHttpClient httpClient;
    private XPath path;
    private String token;
    private Integer loginCheckCounter;

    private static final Logger log = LoggerFactory.getLogger(GlpiService.class);

    public GlpiService(HttpClientService httpClientService) {
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

    public void getTicket(String glpiId, Handler<AsyncResult<Document>> handler) {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><methodCall><methodName>glpi.getTicket</methodName><params><param><value><struct>" +
                "<member><name>id2name</name><value><string></string></value></member>" +
                "<member><name>ticket</name><value><string>" + glpiId + "</string></value></member>" +
                GlpiConstants.END_XML_FORMAT;


        this.sendRequest("POST", ConfigManager.getInstance().getGlpiRootUri(), ParserTool.getParsedXml(xml), result -> {
            if (result.succeeded()) {
                handler.handle(Future.succeededFuture(result.result()));
            } else {
                handler.handle(Future.failedFuture(result.cause().getMessage()));
            }
        });

    }

    public void getIdFromGlpiTicket(Document xmlTicket, Handler<AsyncResult<String>> handler) {
        try {
            String expression = "//methodResponse/params/param/value/struct/member";

            NodeList fields = (NodeList) path.evaluate(expression, xmlTicket, XPathConstants.NODESET);
            this.findFieldValue(fields, GlpiConstants.ID_FIELD, handler);
        } catch (Exception e) {
            handler.handle(Future.failedFuture(e));
        }
    }

    public void getIdFromGlpiTicket(Node xmlTicket, Handler<AsyncResult<String>> handler) {
        try {
            NodeList fields = (NodeList) path.evaluate("member", xmlTicket, XPathConstants.NODESET);
            this.findFieldValue(fields, GlpiConstants.ID_FIELD, handler);
        } catch (Exception e) {
            handler.handle(Future.failedFuture(e));
        }
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
    public void sendRequest(String method, String uri, Document xmlData, Handler<AsyncResult<Document>> handler) {
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

        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }

    }

    private void setHeaderRequest(PivotHttpClientRequest request) {
        request.getHttpClientRequest().putHeader("Content-Type", "text/xml");
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
                    handler.handle(Future.failedFuture("An error occurred at login to GLPI: " + ParserTool.getStringFromDocument(xmlResult)));
                }
                return;
            }
        }
        handler.handle(Future.succeededFuture(true));
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
