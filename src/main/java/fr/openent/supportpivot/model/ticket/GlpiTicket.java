package fr.openent.supportpivot.model.ticket;

import fr.openent.supportpivot.Supportpivot;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.text.ParseException;
import java.util.Date;
import java.util.LinkedList;

public class GlpiTicket implements Ticket{

    private String title;
    private String academieGlpi;
    private String content;
    private Integer priority;
    private String type;
    private String status;
    private Date date;
    private String attributed;
    private String users;

    private LinkedList<String> followUps = new LinkedList<String>();
    private LinkedList<String> documents = new LinkedList<String>();

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public String getCollectivity(){
        return Supportpivot.appConfig.getDefaultCollectivity();
    }

    @Override
    public String getAcademy(){
        return academieGlpi;
    }

    @Override
    public String getUsers(){
        return users;
    }

    @Override
    public String getStatus() {
        return status;
    }

    @Override
    public String getContent(){
        return content;
    }

    @Override
    public Integer getPriority(){
        return priority;
    }

    @Override
    public String getType() { return type; }

    @Override
    public JsonArray getComments() { return null; }

    @Override
    public JsonArray getPjs() { return null; }

    @Override
    public String getCreatedAt() { //TODO
        return null;
    }

    @Override
    public Date getUpdatedAt() { //TODO
        return null;
    }

    @Override
    public Date getSolvedAt() { //TODO
        return null;
    }

    public Date getDate() { return date; }

    @Override
    public String getAttributed() { return attributed; }


}
