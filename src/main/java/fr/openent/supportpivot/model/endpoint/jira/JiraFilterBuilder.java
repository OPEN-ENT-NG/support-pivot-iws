package fr.openent.supportpivot.model.endpoint.jira;

import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class JiraFilterBuilder extends JsonObject {

    private static final Logger log = LoggerFactory.getLogger(JiraFilterBuilder.class);
    StringBuilder jqlQeryString = new StringBuilder();
    String outputFields = "";

    public void addAssigneeFilter(String assignee) {
        if (assignee != null) jqlQeryString.append(addFilter(("assignee = " + assignee)));
    }

    public void addMinUpdateDate(String date) {
        if(date != null && !date.isEmpty()) {
            // format date
            jqlQeryString.append(addFilter("updated > '" + date + "'"));
        }
    }

    public void addCustomfieldFilter(String customfieldid, String value) {
        if (customfieldid != null && value != null)
            jqlQeryString.append(addFilter(("cf[" + customfieldid + "] ~ " + value)));
    }

    public void onlyIds() {
        outputFields = "&fields=id,key";
    }

    public void addFieldDates() {
        addFields(",updated,created");
    }

    public void addFields(String fields) {
        if(outputFields.isEmpty()) {
            outputFields = "&fields=" + fields;
        } else {
            outputFields += "," + fields;
        }
    }

    public String buildSearchQueryString() {
        try {
            return "jql=" + URLEncoder.encode(jqlQeryString.toString(), StandardCharsets.UTF_8.toString()) + outputFields;
        } catch (UnsupportedEncodingException e) {
            log.error("Error during build Jira search request :" + jqlQeryString.toString());
            return "";
        }
    }

    private String addFilter(String filter) {

        if (jqlQeryString.length() == 0) {
            return filter;
        } else {
            return " AND " + filter;
        }
    }

}
