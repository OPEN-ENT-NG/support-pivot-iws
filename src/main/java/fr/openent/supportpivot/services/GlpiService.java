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

    private static final int MAX_LOGIN_TRY = 3;
    private PivotHttpClient httpClient;
    private XPath path;
    private static String token;
    private Integer loginCheckCounter;

    private static final Logger log = LoggerFactory.getLogger(GlpiService.class);

    public GlpiService(HttpClientService httpClientService) {
        loginCheckCounter = 0;
        XPathFactory xpf = XPathFactory.newInstance();
        path = xpf.newXPath();
        token = "";

        try {
            httpClient = httpClientService.getHttpClient(ConfigManager.getInstance().getGlpiHost());
        } catch (URISyntaxException e) {
            log.error("invalid uri " + e);
        }
    }



    public void getIdFromGlpiTicket(Document xmlTicket, Handler<AsyncResult<String>> handler) {
        try {
            String expression = "//methodResponse/params/param/value/struct/member";

            NodeList fields = (NodeList) path.evaluate(expression, xmlTicket, XPathConstants.NODESET);
            findFieldValue(fields, GlpiConstants.ID_FIELD, handler);
        } catch (Exception e) {
            handler.handle(Future.failedFuture(e));
        }
    }

    public String getIdFromGlpiTicket(Node xmlTicket) {
        try {
            String id =  (String) path.evaluate("member[name='"+GlpiConstants.ID_FIELD+"']/value/string", xmlTicket, XPathConstants.STRING);
            return id.trim();
        } catch (Exception e){
            log.warn("ticket id is un reachable in " + xmlTicket.getTextContent());
            return null;
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
        setXmlToken(xmlData);
        log.info(ParserTool.getStringFromDocument(xmlData));
        PivotHttpClientRequest sendingRequest = httpClient.createRequest(method, uri, ParserTool.getStringFromDocument(xmlData));
        setHeaderRequest(sendingRequest);

        sendingRequest.startRequest(result -> {
            if (result.succeeded()) {
                if (result.result().statusCode()>=200 && result.result().statusCode()<400) {
                    result.result().bodyHandler(body -> {
                        Document xml = ParserTool.getParsedXml(body);
                        noReloginCheck(xml, loginResult -> {
                            if (loginResult.succeeded()) {
                                if (loginResult.result()) {
                                    handler.handle(Future.succeededFuture(xml));
                                } else {
                                    log.info("login has been renewed => try again " + method + ", uri: " + uri);
                                    sendRequest(method, uri, xmlData, requestResult -> {
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
                                log.error("Authentication failed. Method: " + method + ", uri: " + uri);
                                log.error("Authentication failed. XmlData :" + ParserTool.getStringFromDocument(xmlData));
                                handler.handle(Future.failedFuture("Authentication problem: " + loginResult.cause().getMessage()));
                            }
                        });
                    });
                }else{
                    log.error("Sending Request failed, code :"+ result.result().statusCode() +" . Method: " + method + ", uri: " + uri);
                    handler.handle(Future.failedFuture("Sending request failed : " + result.result().statusCode()));
                }
            } else {
                log.error("Authentication failed. Method: " + method + ", uri: " + uri);
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
            //remove node sessions if exists
            String expression = "//methodCall/params/param/value/struct/member[name='session']";
            Node session = (Node) path.evaluate(expression, xml, XPathConstants.NODE);
            if (session != null)  session.getParentNode().removeChild(session);
            /*for (int i = 0; i < fields.getLength(); i++) {
                NodeList name = (NodeList) path.compile("name").evaluate(fields.item(i), XPathConstants.NODESET);
                if (name.item(0).getTextContent().equals("session")) {
                    NodeList value = (NodeList) path.compile("value").evaluate(fields.item(i), XPathConstants.NODESET);
                    value.item(0).setNodeValue(token);
                    return;
                }
            }*/

            //add session node with fresh token
            expression = "//methodCall/params/param/value/struct";
            Node struct = ((NodeList) path.evaluate(expression, xml, XPathConstants.NODESET)).item(0);

            Element member = xml.createElement("member");
            Element name = xml.createElement("name");
            Element value = xml.createElement("value");
            Element stringValue = xml.createElement("string");

            name.setTextContent("session");
            stringValue.setTextContent(token);

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
        if (loginCheckCounter == MAX_LOGIN_TRY) {
            handler.handle(Future.failedFuture("Login maximum ("+ MAX_LOGIN_TRY  +  ") attempts reached"));
            return;
        }

        NodeList nodeList = xmlResult.getElementsByTagName("member");
        for (int i = 0; i < nodeList.getLength(); i++) {
            Element session = (Element) nodeList.item(i);
            String fieldName = session.getElementsByTagName("name").item(0).getTextContent();
            if (fieldName.equals(GlpiConstants.ERROR_CODE_NAME)) {
                log.error("GLPI request an Error occured " + ParserTool.getStringFromDocument(xmlResult));
                String fieldValue = session.getElementsByTagName("value").item(0).getTextContent().trim();

                //Bad glpi webservice implementation : authentication error can thrown with code 3 also
                if (fieldValue.equals(GlpiConstants.ERROR_LOGIN_CODE) || fieldValue.equals("3")) {
                    //if it's an authentication error
                    loginCheckCounter++;
                    LoginTool.getGlpiSessionToken(httpClient, result -> {
                        if (result.succeeded()) {

                            token = result.result().trim();
                            loginCheckCounter = 0;
                            log.info("Glpi session id is gotten : " + token);
                            //token is renewed => initial request must be retried
                            handler.handle(Future.succeededFuture(false));
                        } else {
                            //error when renew token => try again
                            noReloginCheck(xmlResult, loginResult -> {
                                if (loginResult.succeeded()) {
                                    handler.handle(Future.succeededFuture(loginResult.result()));
                                } else {
                                    handler.handle(Future.failedFuture(loginResult.cause().getMessage()));
                                }
                            });
                        }
                    });
                } else {
                    handler.handle(Future.failedFuture("An error occurred when request to GLPI: " + ParserTool.getStringFromDocument(xmlResult)));
                }
                return;
            }
        }
        //No error code => request has been successful
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
