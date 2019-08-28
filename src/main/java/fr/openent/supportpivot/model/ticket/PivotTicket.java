package fr.openent.supportpivot.model.ticket;

import fr.openent.supportpivot.constants.PivotConstants;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;

public class PivotTicket implements Ticket{

    private String title;
    private String academieGlpi;
    private String content;
    private Integer priority;
    private String type;
    private Date date;
    private String attributed;
    private String users;
    private String status;

    private LinkedList<String> followUps = new LinkedList<String>();
    private LinkedList<String> documents = new LinkedList<String>();

    private JsonObject jsonTicket = new JsonObject();
    private SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public PivotTicket () {
        this.setAttributed();
    }

    public Integer getGlpiId(){
        try {
            return Integer.parseInt(jsonTicket.getString(PivotConstants.IDGLPI_FIELD));
        } catch(Exception e) {
            return null;
        }
    }
    public Integer getJiraId(){
        return Integer.parseInt(jsonTicket.getString(PivotConstants.IDJIRA_FIELD));
    }
    public Integer getId(){
        return Integer.parseInt(jsonTicket.getString(PivotConstants.ID_FIELD));
    }

    public JsonObject getJsonTicket() {
        return this.jsonTicket;
    }


    @Override
    public String getTitle() {
        return jsonTicket.getString(PivotConstants.TITLE_FIELD);
    }

    @Override
    public String getCollectivity(){
        return jsonTicket.getString(PivotConstants.COLLECTIVITY_FIELD);
    }

    @Override
    public String getAcademy(){
        return jsonTicket.getString(PivotConstants.ACADEMY_FIELD);
    }

    @Override
    public String getUsers(){ return jsonTicket.getString(PivotConstants.CREATOR_FIELD); }

    @Override
    public String getContent(){ return jsonTicket.getString(PivotConstants.DESCRIPTION_FIELD); }

    @Override
    public String getStatus() { return jsonTicket.getString(PivotConstants.STATUSENT_FIELD); }

    @Override
    public Integer getPriority(){
        return Integer.parseInt(jsonTicket.getString(PivotConstants.PRIORITY_FIELD));
    }

    @Override
    public String getType() { return jsonTicket.getString(PivotConstants.TICKETTYPE_FIELD); }

    @Override
    public JsonArray getComments() { return jsonTicket.getJsonArray(PivotConstants.COMM_FIELD); }

    @Override
    public JsonArray getPj() { return jsonTicket.getJsonArray(PivotConstants.ATTACHMENT_FIELD); }
    /*#### DATES ####*/

    public String getCreatedAt(){
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
    public String getAttributed() { return jsonTicket.getString(PivotConstants.ATTRIBUTION_FIELD); }


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

    //TODO Change the attribution to the constant => we need to know if the ticket is attributed to support-pivot,
    //TODO if the ticket come from Glpi.
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
        this.jsonTicket = ticket;
    }
}
