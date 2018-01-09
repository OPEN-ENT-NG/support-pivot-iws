package fr.openent.supportpivot.service;

import fr.wseduc.webutils.Either;
import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonObject;

/**
 * Created by colenot on 07/12/2017.
 */
public interface DemandeService {
    public void add(HttpServerRequest request, JsonObject ressource, Handler<Either<String, JsonObject>> handler);
    public void addIWS(HttpServerRequest request, JsonObject ressource, Handler<Either<String, JsonObject>> handler);
    public void addENT(HttpServerRequest request, JsonObject ressource, Handler<Either<String, JsonObject>> handler);
    public void sendToIWS (HttpServerRequest request, JsonObject ressource, Handler<Either<String, JsonObject>> handler);
}
