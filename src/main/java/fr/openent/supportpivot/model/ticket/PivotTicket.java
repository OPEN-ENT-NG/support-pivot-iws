package fr.openent.supportpivot.model.ticket;

import fr.openent.supportpivot.constants.PivotConstants;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class PivotTicket implements Ticket {

    private JsonArray comments = new JsonArray();
    private JsonArray pjs = new JsonArray();

    private JsonObject jsonTicket = new JsonObject();
    private SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public PivotTicket() {
        this.initComments();
        this.initPjs();
    }

    private void initComments() {
        if (this.getComments() == null) {
            this.jsonTicket.put(PivotConstants.COMM_FIELD, this.comments);
        }
    }

    private void initPjs() {
        if (this.getPjs() == null) {
            this.jsonTicket.put(PivotConstants.ATTACHMENT_FIELD, this.pjs);
        }
    }

    public String getGlpiId() {
        return jsonTicket.getString(PivotConstants.IDGLPI_FIELD, null);
    }

    public String getJiraId() {
        return jsonTicket.getString(PivotConstants.IDJIRA_FIELD, null);
    }

    public String getIwsId() { return jsonTicket.getString(PivotConstants.IDIWS_FIELD, null); }

    public String getId() {
        return jsonTicket.getString(PivotConstants.ID_FIELD, null);
    }

    public JsonObject getJsonTicket() {
        if (this.jsonTicket == null) {
            this.jsonTicket = new JsonObject();
        }
        return this.jsonTicket;
    }


    @Override
    public String getTitle() {
        return jsonTicket.getString(PivotConstants.TITLE_FIELD);
    }

    @Override
    public String getCollectivity() {
        return jsonTicket.getString(PivotConstants.COLLECTIVITY_FIELD);
    }

    @Override
    public String getAcademy() {
        return jsonTicket.getString(PivotConstants.ACADEMY_FIELD);
    }

    @Override
    public String getUsers() {
        return jsonTicket.getString(PivotConstants.CREATOR_FIELD);
    }

    @Override
    public String getContent() {
        return jsonTicket.getString(PivotConstants.DESCRIPTION_FIELD);
    }

    @Override
    public String getStatus() {
        return jsonTicket.getString(PivotConstants.STATUSENT_FIELD);
    }

    @Override
    public Integer getPriority() {
        return Integer.parseInt(jsonTicket.getString(PivotConstants.PRIORITY_FIELD));
    }

    @Override
    public String getType() {
        return jsonTicket.getString(PivotConstants.TICKETTYPE_FIELD);
    }

    @Override
    public JsonArray getComments() {
        return jsonTicket.getJsonArray(PivotConstants.COMM_FIELD);
    }

    @Override
    public JsonArray getPjs() {
        return jsonTicket.getJsonArray(PivotConstants.ATTACHMENT_FIELD);
    }
    /*#### DATES ####*/

    public String getCreatedAt() {
        return jsonTicket.getString(PivotConstants.DATE_CREA_FIELD);
    }

    public Date getUpdatedAt() throws ParseException {
        return this.parser.parse(jsonTicket.getString(PivotConstants.DATE_MOD_FIELD));
    }

    public Date getSolvedAt() throws ParseException {
        return this.parser.parse(jsonTicket.getString(PivotConstants.DATE_RESO_FIELD));
    }

    public Date getGlpiCreatedAt() throws ParseException {
        return this.parser.parse(jsonTicket.getString(PivotConstants.DATE_CREAGLPI_FIELD));
    }

    public Date getGlpiUpdatedAt() throws ParseException {
        return this.parser.parse(jsonTicket.getString(PivotConstants.DATE_MODGLPI_FIELD));
    }

    public Date getGlpiSolvedAt() throws ParseException {
        return this.parser.parse(jsonTicket.getString(PivotConstants.DATE_RESOGLPI_FIELD));
    }

    public Date getJiraCreatedAt() throws ParseException {
        return parser.parse(jsonTicket.getString(PivotConstants.DATE_CREAJIRA_FIELD));
    }

    public Date getJiraUpdatedAt() throws ParseException {
        return this.parser.parse(jsonTicket.getString(PivotConstants.DATE_MODJIRA_FIELD));
    }

    public Date getJiraSolvedAt() throws ParseException {
        return this.parser.parse(jsonTicket.getString(PivotConstants.DATE_RESOJIRA_FIELD));
    }

    @Override
    public String getAttributed() {
        return jsonTicket.getString(PivotConstants.ATTRIBUTION_FIELD);
    }


    /* SETTERS */

    public void setTitle(String title) {
        jsonTicket.put(PivotConstants.TITLE_FIELD, title.trim());
    }

    public void setContent(String content) {
        jsonTicket.put(PivotConstants.DESCRIPTION_FIELD, content.trim());
    }

    public void setPriority(String priority) {
        jsonTicket.put(PivotConstants.PRIORITY_FIELD, priority.trim());
    }

    public void setType(String type) {
        jsonTicket.put(PivotConstants.TICKETTYPE_FIELD, type.trim());
    }

    public void setDate(Date date) {
        jsonTicket.put(PivotConstants.DATE_CREA_FIELD, date);
    }

    public void setAttributed() {
        jsonTicket.put(PivotConstants.ATTRIBUTION_FIELD, PivotConstants.ATTRIBUTION_NAME);
    }

    public void setStatus(String status, String attributedName) {
        if (attributedName.equals(PivotConstants.ATTRIBUTION_GLPI)) {
            jsonTicket.put(PivotConstants.STATUSGLPI_FIELD, status);
        } else {
            jsonTicket.put(PivotConstants.STATUSENT_FIELD, status);
        }
    }

    public void setGlpiId(String glpiId) {
        jsonTicket.put(PivotConstants.IDGLPI_FIELD, glpiId.trim());
    }

    public void setJiraId(String glpiId) {
        jsonTicket.put(PivotConstants.IDJIRA_FIELD, glpiId.trim());
    }

    public void setCreator(String creator) {
        jsonTicket.put(PivotConstants.CREATOR_FIELD, creator.trim());
    }

    public void setGlpiCreatedAt(String createdAt) {
        jsonTicket.put(PivotConstants.DATE_CREAGLPI_FIELD, createdAt.trim());
    }


    public void setGlpiUpdatedAt(String updatedAt) {
        jsonTicket.put(PivotConstants.DATE_MODGLPI_FIELD, updatedAt.trim());
    }

    public void setGlpiSolvedAt(String solvedAt) {
        jsonTicket.put(PivotConstants.DATE_RESOGLPI_FIELD, solvedAt.trim());
    }

    public void setCategorie(String categorie) { // TODO => mapping
        jsonTicket.put(PivotConstants.MODULESGLPI_FIELD, categorie.trim());
    }

    public void setJsonObject(JsonObject ticket) {
        if (ticket != null) {
            this.jsonTicket = ticket;
            this.initComments();
            this.initPjs();
        }
    }

    public void setId(String id) {
        jsonTicket.put(PivotConstants.ID_FIELD, id.trim());
    }

    public void setIwsId(String id) {
        jsonTicket.put(PivotConstants.IDIWS_FIELD, id.trim());
    }

    public void setComments(JsonArray comments) {
        this.comments = comments;
    }

    public void setPjs(JsonArray pjs) {
        this.pjs = pjs;
    }

    public void addComment(JsonObject comment) {
        this.comments.add(comment);
    }

    public void addPj(JsonObject pj) {
        this.pjs.add(pj);
    }
}
