package fr.openent.supportpivot.service.impl;

import fr.openent.supportpivot.service.DemandeService;
import fr.wseduc.mongodb.MongoDb;
import fr.wseduc.webutils.Either;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;

/**
 * Created by colenot on 07/12/2017.
 */
public class DefaultDemandeServiceImpl implements DemandeService {

    private final String DEMANDE_COLLECTION = "support.demandes";
    private final MongoDb mongo;

    public DefaultDemandeServiceImpl() {
        this.mongo = MongoDb.getInstance();
    }

    @Override
    public void add(JsonObject resource, final Handler<Either<String, JsonObject>> handler) {
        mongo.insert(DEMANDE_COLLECTION, resource, new Handler<Message<JsonObject>>() {
            @Override
            public void handle(Message<JsonObject> retourJson) {
                JsonObject body = retourJson.body();
                if("ok".equals(body.getString("status"))) {
                    handler.handle(new Either.Right<String, JsonObject>(new JsonObject().putString("status","OK")));
                }
                else {
                    handler.handle(new Either.Left<String, JsonObject>("KO"));
                }

            }
        });
    }
}
