package fr.openent.supportpivot.helpers;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class JsonObjectSafe extends JsonObject {
    public void putSafe(String key, String value) {
        if(value != null) {
            this.put(key, value);
        }
    }
    public void putSafe(String key, JsonObject value) {
        if(value != null) {
            this.put(key, value);
        }
    }
    public void putSafe(String key, JsonArray value) {
        if(value != null) {
            this.put(key, value);
        }
    }
}
