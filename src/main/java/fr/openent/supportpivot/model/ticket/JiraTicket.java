package fr.openent.supportpivot.model.ticket;

import fr.openent.supportpivot.managers.ConfigManager;
import fr.openent.supportpivot.services.JiraService;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;

public class JiraTicket extends JsonObject {


    private static final String PROJECT_FIELDNAME= "project";
    private static final String PROJECT_KEY_FIELDNAME = "key";
    private static final String PROJET_KEY_VALUE = ConfigManager.getInstance().getJiraProjectKey();

    private static final String SUMMARY_FIELDNAME = "summary";
    private static final String DESCRITPION_FIELDNAME = "description";
    private static final String TYPE_FIELDNAME = "issuetype";
    private static final String LABELS_FIELDNAME = "labels";
    private static final String PRIORITY_FIELDNAME = "priority";
    private static final String CREATOR_CONFIGKEY = "creator";

    private static final JsonObject CUSTOMFIELD_FIELDNAMES = ConfigManager.getInstance().getJiraCustomFields();
    private static final String ID_ENT_CF_CONFIGKEY = "id_ent";
    private static final String ID_IWS_CF_CONFIGKEY = "id_iws";
    private static final String STATUT_ENT_CF_CONFIGKEY = "status_ent";
    private static final String STATUT_IWS_CF_CONFIGKEY = "status_iws";
    private static final String RESOLUTION_ENT_CF_CONFIGKEY = "resolution_ent";
    private static final String RESOLUTION_IWS_CF_CONFIGKEY = "resolution_iws";
    private static final String RESPONSE_TECHNICAL_CF_CONFIGKEY = "response_technical";

    private JsonObject ticket = new JsonObject();

    public JsonObject getJiraTicket(){
        return ticket;
    }

    public JiraTicket(){
        ticket.put("fields", new JsonObject().put(PROJECT_FIELDNAME, new JsonObject()
                .put(PROJECT_KEY_FIELDNAME, PROJET_KEY_VALUE)));
    }

    public JiraTicket(PivotTicket pivotTicket) {
        super();
        addFields(SUMMARY_FIELDNAME, pivotTicket.getTitle());
        addFields(DESCRITPION_FIELDNAME, pivotTicket.getContent());
        addFields(SUMMARY_FIELDNAME, pivotTicket.getTitle());
        addFields(SUMMARY_FIELDNAME, pivotTicket.getTitle());
    }

    private void addFields(String key, Object value){
        if(value!= null && key != null){
            ticket.getJsonObject("fields").put(key, value);
        }
    }


/*

    jsonJiraTicket.put("fields", new JsonObject()
                .put("project", new JsonObject()
                        .put("key", JIRA_PROJECT_NAME))
            .put("summary", jsonPivotIn.getString(TITLE_FIELD))
            .put("description", jsonPivotIn.getString(DESCRIPTION_FIELD))
            .put("issuetype", new JsonObject()
                        .put("name", ticketType))
            .put("labels", jsonPivotIn.getJsonArray(MODULES_FIELD))
            .put(JIRA_FIELD.getString("id_ent"), jsonPivotIn.getString(ID_FIELD))
            .put(JIRA_FIELD.getString("id_iws"), jsonPivotIn.getString(IDIWS_FIELD))
            .put(JIRA_FIELD.getString("status_ent"), jsonPivotIn.getString(STATUSENT_FIELD))
            .put(JIRA_FIELD.getString("status_iws"), jsonPivotIn.getString(STATUSIWS_FIELD))
            .put(JIRA_FIELD.getString("creation"), jsonPivotIn.getString(DATE_CREA_FIELD))
            .put(JIRA_FIELD.getString("resolution_ent"), jsonPivotIn.getString(DATE_RESO_FIELD))
            .put(JIRA_FIELD.getString("resolution_iws"), jsonPivotIn.getString(DATE_RESOIWS_FIELD))
            .put(JIRA_FIELD.getString("creator"), jsonPivotIn.getString(CREATOR_FIELD))
            .put("priority", new JsonObject()
                        .put("name", currentPriority)));

 */
}
