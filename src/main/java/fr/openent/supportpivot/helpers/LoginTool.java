package fr.openent.supportpivot.helpers;

import fr.openent.supportpivot.constants.GlpiConstants;
import fr.openent.supportpivot.managers.ConfigManager;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class LoginTool {

    public static void getGlpiSessionToken(PivotHttpClient httpClient, Handler<AsyncResult<String>> handler) {
        try {
            PivotHttpClientRequest sendingRequest = httpClient.createPostRequest(ConfigManager.getInstance().getGlpiRootUri(),
                    "<?xml version='1.0' encoding=\"utf-8\" ?><methodCall><methodName>glpi.doLogin</methodName><params><param><value><struct>" +
                            "<member><name>login_name</name><value><string>" + ConfigManager.getInstance().getGlpiLogin() + "</string></value></member>" +
                            "<member><name>login_password</name><value><string>" + ConfigManager.getInstance().getGlpiPassword() + "</string></value></member>" +
                            "</struct></value></param></params></methodCall>"
            );

            sendingRequest.getHttpClientRequest().putHeader("Content-Type", "text/xml");

            sendingRequest.startRequest(result -> {
                if (result.succeeded()) {
                    result.result().bodyHandler(body -> {
//                        handler.handle(Future.succeededFuture(body.toJsonObject().getValue("session").toString()));
                        Document xml = ParserTool.getParsedXml(body);
                        NodeList nodeList = xml.getElementsByTagName("member");
                        if (nodeList.getLength() < 5) {
                            //TODO ajouter un log : url, xmldata
                            handler.handle(Future.failedFuture("Support Pivot GLPI - login failed"));
                        }
                        Element session = (Element) nodeList.item(4);

                        String sessionName = session.getElementsByTagName("name").item(0).getTextContent();
                        if (sessionName.equals("session")) {
                            String sessionValue = session.getElementsByTagName("value").item(0).getTextContent();
                            handler.handle(Future.succeededFuture(sessionValue));
                        } else {
                            handler.handle(Future.failedFuture("no session"));
                        }
                    });
                } else {
                    handler.handle(Future.failedFuture("result error"));
                }
            });
        } catch (Exception ex) {
            handler.handle(Future.failedFuture(ex));
        }
    }
}
