package fr.openent.supportpivot.service;

import fr.wseduc.webutils.Either;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonObject;

/**
 * Created by colenot on 07/12/2017.
 */
public interface DemandeService {
    public void add(JsonObject ressource, Handler<Either<String, JsonObject>> handler);
}
