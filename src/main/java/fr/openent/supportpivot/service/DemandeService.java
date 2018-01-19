package fr.openent.supportpivot.service;

import fr.wseduc.webutils.Either;
import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonObject;

/**
 * Created by colenot on 07/12/2017.
 *
 * Service to handle pivot information and send at the right place
 */
public interface DemandeService {
    void add(HttpServerRequest request, JsonObject ressource, Handler<Either<String, JsonObject>> handler);
    void addIWS(HttpServerRequest request, JsonObject ressource, Handler<Either<String, JsonObject>> handler);
    void addENT(HttpServerRequest request, JsonObject ressource, Handler<Either<String, JsonObject>> handler);
    void sendToIWS (HttpServerRequest request, JsonObject ressource, Handler<Either<String, JsonObject>> handler);
    void sendToCGI (HttpServerRequest request, JsonObject ressource, Handler<Either<String, JsonObject>> handler);

    void testMailToIWS(HttpServerRequest request, String mailTo, Handler<Either<String, JsonObject>> handler);
}
