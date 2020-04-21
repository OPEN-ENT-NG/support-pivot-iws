package fr.openent.supportpivot.model.ticket;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class PivotTicket {

    public final static String IDEXTERNAL_FIELD = "id_externe";
    @Deprecated
    public final static String IDIWS_FIELD = "id_iws";
    public final static String ID_FIELD = "id_ent";
    public final static String IDJIRA_FIELD = "id_jira";
    public final static String COLLECTIVITY_FIELD = "collectivite";
    public final static String ACADEMY_FIELD = "academie";
    public final static String CREATOR_FIELD = "demandeur";
    public final static String TICKETTYPE_FIELD = "type_demande";
    public final static String TITLE_FIELD = "titre";
    public final static String DESCRIPTION_FIELD = "description";
    public final static String PRIORITY_FIELD = "priorite";
    public final static String MODULES_FIELD = "modules";
    public final static String COMM_FIELD = "commentaires";
    public final static String ATTACHMENT_FIELD = "pj";
    public final static String ATTACHMENT_NAME_FIELD = "nom";
    public final static String ATTACHMENT_CONTENT_FIELD = "contenu";
    public final static String STATUSEXTERNAL_FIELD = "statut_external";
    @Deprecated
    public final static String STATUSIWS_FIELD = "statut_iws";
    public final static String STATUSENT_FIELD = "statut_ent";
    public final static String STATUSJIRA_FIELD = "statut_jira";
    public final static String DATE_CREA_FIELD = "date_creation";
    public final static String RAWDATE_CREA_FIELD = "creation";
    public final static String RAWDATE_UPDATE_FIELD = "maj";
    @Deprecated
    public final static String DATE_RESOIWS_FIELD = "date_resolution_iws";
    public final static String DATE_RESOEXTERNAL_FIELD = "date_resolution_externe";
    public final static String DATE_RESO_FIELD = "date_resolution_ent";
    public final static String DATE_RESOJIRA_FIELD = "date_resolution_jira";
    public final static String TECHNICAL_RESP_FIELD= "reponse_technique";
    public final static String CLIENT_RESP_FIELD= "reponse_client";
    public final static String ATTRIBUTION_FIELD = "attribution";
    public final static String UAI_FIELD = "uai";

    private JsonObject jsonTicket = new JsonObject();
    private SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public PivotTicket() {
        this.initComments();
        this.initPjs();
    }

    private void initComments() {
        if (this.getComments() == null) {
            this.jsonTicket.put(COMM_FIELD, new JsonArray());
        }
    }

    private void initPjs() {
        if (this.getPjs() == null) {
            this.jsonTicket.put(ATTACHMENT_FIELD, new JsonArray());
        }
    }

    public String getJiraId() {
        return jsonTicket.getString(IDJIRA_FIELD, null);
    }
    
    public String getExternalId() { return jsonTicket.getString(IDEXTERNAL_FIELD, jsonTicket.getString(IDIWS_FIELD, null)); }



    public String getId() {
        return jsonTicket.getString(ID_FIELD, null);
    }

    public JsonObject getJsonTicket() {
        if (this.jsonTicket == null) {
            this.jsonTicket = new JsonObject();
        }
        return this.jsonTicket;
    }


    public String getTitle() {
        return jsonTicket.getString(TITLE_FIELD);
    }

    public String getCollectivity() {
        return jsonTicket.getString(COLLECTIVITY_FIELD);
    }

    public String getAcademy() {
        return jsonTicket.getString(ACADEMY_FIELD);
    }

    public String getUsers() {
        return jsonTicket.getString(CREATOR_FIELD);
    }

    public String getContent() {
        return jsonTicket.getString(DESCRIPTION_FIELD);
    }

    public String getStatus() {
        return jsonTicket.getString(STATUSENT_FIELD);
    }

    public Integer getPriority() {
        return Integer.parseInt(jsonTicket.getString(PRIORITY_FIELD));
    }

    public String getType() {
        return jsonTicket.getString(TICKETTYPE_FIELD);
    }

    public JsonArray getComments() {
        return this.jsonTicket.getJsonArray(COMM_FIELD);
    }

    public JsonArray getPjs() { return this.jsonTicket.getJsonArray(ATTACHMENT_FIELD); }

    public String getUai() { return this.jsonTicket.getString(UAI_FIELD); }

    /*#### DATES ####*/

    public String getCreatedAt() {
        return jsonTicket.getString(DATE_CREA_FIELD);
    }

    public String getRawCreatedAt() {
        return jsonTicket.getString(RAWDATE_CREA_FIELD);
    }

    public String getRawUpdatedAt() {
        return jsonTicket.getString(RAWDATE_UPDATE_FIELD);
    }

    public Date getSolvedAt() throws ParseException {
        return this.parser.parse(jsonTicket.getString(DATE_RESO_FIELD));
    }
    public Date getSolvedxternalAt() throws ParseException {
        return this.parser.parse(jsonTicket.getString(DATE_RESOEXTERNAL_FIELD));
    }

    public Date getSolvedJIRAAt() throws ParseException {
        return this.parser.parse(jsonTicket.getString(DATE_RESOJIRA_FIELD));
    }


    public String getAttributed() {
        return jsonTicket.getString(ATTRIBUTION_FIELD);
    }


    /* SETTERS */

    public void setTitle(String title) {
        jsonTicket.put(TITLE_FIELD, title.trim());
    }

    public void setContent(String content) {
        jsonTicket.put(DESCRIPTION_FIELD, content.trim());
    }

    public void setPriority(String priority) {
        jsonTicket.put(PRIORITY_FIELD, priority.trim());
    }

    public void setType(String type) {
        jsonTicket.put(TICKETTYPE_FIELD, type.trim());
    }

    public void setDate(Date date) {
        jsonTicket.put(DATE_CREA_FIELD, date);
    }

    public void setAttributed(String attribution) {
        jsonTicket.put(ATTRIBUTION_FIELD, attribution);
    }

    public void setStatus(String status) {
            jsonTicket.put(STATUSENT_FIELD, status);
        }
    public void setStatusExternal(String status) {
        jsonTicket.put(STATUSEXTERNAL_FIELD, status);
    }
    public void setStatusJIRA(String status) {
        jsonTicket.put(STATUSJIRA_FIELD, status);
    }


    public void setJiraId(String jiraId) {
        jsonTicket.put(IDJIRA_FIELD, jiraId.trim());
    }

    public void setCreator(String creator) {
        jsonTicket.put(CREATOR_FIELD, creator.trim());
    }

    public PivotTicket setJsonObject(JsonObject ticket) {
        if (ticket != null) {
            this.jsonTicket = ticket;
            this.initComments();
            this.initPjs();
        }
        return this;
    }

    public void setId(String id) {
        jsonTicket.put(ID_FIELD, id.trim());
    }

    //Retrocompatibility use id_iws as field for external tool id
    public void setExternalId(String id) {
        jsonTicket.put(IDIWS_FIELD, id.trim());
        jsonTicket.put(IDEXTERNAL_FIELD, id.trim());
    }

    public void setComments(JsonArray comments) {this.jsonTicket.put(COMM_FIELD, comments);}

    public void setPjs(JsonArray pjs) { this.jsonTicket.put(ATTACHMENT_FIELD, pjs); }

    public void addComment(JsonObject comment) {
        this.getComments().add(comment);
    }

    public void addPj(JsonObject pj) {
        this.getPjs().add(pj);
    }

    public static class Attachment extends  JsonObject{

        public void setName(String name){ put(ATTACHMENT_NAME_FIELD, name); }

        public void setContent(String content){ put(ATTACHMENT_CONTENT_FIELD, content); }
    }
}
