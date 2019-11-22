package fr.openent.supportpivot.model.ticket;

import fr.openent.supportpivot.constants.JiraConstants;
import fr.openent.supportpivot.managers.ConfigManager;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.text.ParseException;
import java.util.Date;
import java.util.LinkedList;

public class JiraTicket implements Ticket {

    private String title;
    private String academieGlpi;
    private String content;
    private Integer priority;
    private String type;
    private String status;
    private Date date;
    private String attributed;
    private String users;

    private JsonObject jsonTicket = new JsonObject();


    private LinkedList<String> followUps = new LinkedList<String>();
    private LinkedList<String> documents = new LinkedList<String>();

    public JiraTicket() {
        this.setField();
        this.setProjectKey();
    }

    public JsonObject getField() {
        return jsonTicket.getJsonObject(JiraConstants.FIELDS);
    }

    public JsonObject getJsonTicket() {
        return this.jsonTicket;
    }

    @Override
    public String getTitle() {
        return title;
    }

    @Override
    public String getCollectivity() {
        return ConfigManager.getInstance().getCollectivity();
    }

    @Override
    public String getAcademy() {
        return academieGlpi;
    }

    @Override
    public String getUsers() {
        return users;
    }

    @Override
    public String getStatus() {
        return status;
    }

    @Override
    public String getContent() {
        return content;
    }

    @Override
    public Integer getPriority() {
        return priority;
    }

    @Override
    public String getType() {
        return type;
    }

    @Override
    public JsonArray getComments() {
        return null;
    }

    @Override
    public JsonArray getPjs() {
        return null;
    }

    @Override
    public String getCreatedAt() {
        return null;
    }

    @Override
    public Date getSolvedAt() throws ParseException {
        return null;
    }

    public Date getDate() {
        return date;
    }

    @Override
    public String getAttributed() {
        return attributed;
    }

    /*
     * SETTERS
     */
    private void setField() {
        jsonTicket.put(JiraConstants.FIELDS, new JsonObject());
    }

    private void setProjectKey() {
        this.getField().put(JiraConstants.PROJECT, new JsonObject()
                .put(JiraConstants.PROJECT_KEY, ConfigManager.getInstance().getJiraProjectKey()));
    }

    public void setTitle(String title) {
         this.getField().put(JiraConstants.TITLE_FIELD, title);
    }

    public void setContent(String content) {
        this.getField().put(JiraConstants.DESCRIPTION_FIELD, content);
    }

}
