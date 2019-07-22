package fr.openent.supportpivot.services;

import fr.openent.supportpivot.Supportpivot;
import fr.openent.supportpivot.constants.BusConstants;
import fr.wseduc.mongodb.MongoDb;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MongoService {

    private final MongoDb mongo;
    private final String mongoCollection;

    private static final String SOURCE = "source";
    private static final String DATE = "date";

    private final Logger log = LoggerFactory.getLogger(Supportpivot.class);

    public MongoService(String mongoCollection) {
        this.mongo = MongoDb.getInstance();
        this.mongoCollection = mongoCollection;
    }


    public void saveTicket(final String source, JsonObject jsonPivot) {
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        Date date = new Date();
        jsonPivot.put(SOURCE, source);
        jsonPivot.put(DATE, dateFormat.format(date));

        mongo.insert(mongoCollection, jsonPivot, retourJson -> {
            if (!BusConstants.OK.equals(retourJson.body().getString(BusConstants.STATUS))) {
                log.error("Supportpivot : could not save json to mongoDB");
            }
        });
    }

    public void getMongoInfos(String mailTo,
                              final Handler<AsyncResult<JsonObject>> handler) {
        try {
            JsonObject req = new JsonObject(java.net.URLDecoder.decode(mailTo, "UTF-8"));
            mongo.find(mongoCollection, req,
                    jsonObjectMessage -> handler.handle(Future.succeededFuture(jsonObjectMessage.body())));
        } catch(Exception e) {
            handler.handle(Future.failedFuture("Malformed json"));
        }
    }

}
