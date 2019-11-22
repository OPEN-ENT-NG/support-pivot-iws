package fr.openent.supportpivot.model.ticket;

import io.vertx.core.json.JsonArray;

import java.text.ParseException;
import java.util.Date;
import java.util.LinkedList;

public interface Ticket {

    String getCollectivity();
    String getAcademy();
    String getUsers();
    String getStatus();
    String getTitle();
    String getContent();
    Integer getPriority();
    String getType();
    JsonArray getComments();
    JsonArray getPjs();

    String getCreatedAt();

    Date getSolvedAt() throws ParseException;

    String getAttributed();

}
