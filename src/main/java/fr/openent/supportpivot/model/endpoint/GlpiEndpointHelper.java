package fr.openent.supportpivot.model.endpoint;

import fr.openent.supportpivot.constants.GlpiConstants;
import fr.openent.supportpivot.helpers.ParserTool;
import fr.openent.supportpivot.managers.ConfigManager;
import fr.openent.supportpivot.model.ticket.PivotTicket;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import org.w3c.dom.Document;

public class GlpiEndpointHelper {

    static Document generateXMLRPCCreateTicketQuery(PivotTicket ticket) {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><methodCall><methodName>jira.createTicket</methodName><params><param><value><struct>";
        xml += xmlAddField(GlpiConstants.TITLE_CREATE, "string", ticket.getTitle());
        xml += xmlAddField(GlpiConstants.DESCRIPTION_CREATE, "string", ticket.getContent());
        xml += xmlAddField(GlpiConstants.ENTITY_CREATE, "integer", GlpiConstants.ENTITY_ID);
        xml += xmlAddField(GlpiConstants.CATEGORY_CREATE, "string", ConfigManager.getInstance().getGlpiCategory());
        xml += xmlAddField(GlpiConstants.REQUESTER_CREATE, "integer", GlpiConstants.REQUESTER_ID);
        xml += xmlAddField(GlpiConstants.TYPE_CREATE, "integer", GlpiConstants.TYPE_ID);

        if (ticket.getJiraId() != null) {
            xml += xmlAddField(GlpiConstants.ID_JIRA_CREATE, "string", ticket.getJiraId());
        }

        if (ticket.getId() != null) {
            xml += xmlAddField(GlpiConstants.ID_ENT_CREATE, "string", ticket.getId());
        }

        xml += GlpiConstants.END_XML_FORMAT;
        return ParserTool.getParsedXml(xml);
    }

    static Document generateXMLRPCAddCommentQuery(String id, String comment) {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><methodCall><methodName>glpi.addTicketFollowup</methodName><params><param><value><struct>";

        xml += xmlAddField(GlpiConstants.TICKET_ID_FORM, "string", id);

        xml += xmlAddField(GlpiConstants.CONTENT_COMMENT, "string", comment);

        xml += GlpiConstants.END_XML_FORMAT;
        return ParserTool.getParsedXml(xml);
    }

    static Document generateXMLRPCAddAttachmentsQuery(String id, JsonObject pj) {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><methodCall><methodName>glpi.addTicketDocument</methodName><params><param><value><struct>";
        xml += xmlAddField(GlpiConstants.TICKET_ID_FORM, "string", id);
        xml += xmlAddField(GlpiConstants.ATTACHMENT_NAME_FORM, "string", pj.getString("nom"));
        xml += xmlAddField(GlpiConstants.ATTACHMENT_B64_FORM, "string", pj.getString("contenu"));

        xml += GlpiConstants.END_XML_FORMAT;
        return ParserTool.getParsedXml(xml);
    }

    static Document generateXMLRPCListTicketsQuery(){
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><methodCall><methodName>glpi.listTickets</methodName><params><param><value><struct>" +
                "<member><name>id2name</name><value><string></string></value></member>" +
                GlpiConstants.END_XML_FORMAT;
        return ParserTool.getParsedXml(xml);
    }

    static Document generateXMLRPCGetTicketQuery(String glpiId) {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><methodCall><methodName>jira.getTicket</methodName><params><param><value><struct>" +
                "<member><name>id2name</name><value><string></string></value></member>" +
                "<member><name>ticket</name><value><string>" + glpiId + "</string></value></member>" +
                GlpiConstants.END_XML_FORMAT;

        return ParserTool.getParsedXml(xml);
    }

    static Document generateXMLRPCGetDocumentQuery(String glpiId, String documentId) {
    String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><methodCall><methodName>glpi.getDocument</methodName><params><param><value><struct>" +
            "<member><name>id2name</name><value><string></string></value></member>" +
            "<member><name>ticket</name><value><string>" + glpiId + "</string></value></member>" +
            "<member><name>document</name><value><string>" + documentId + "</string></value></member>" +
            GlpiConstants.END_XML_FORMAT;
        return ParserTool.getParsedXml(xml);
    }

    private static String xmlAddField(String fieldName, String valueType, String value) {
        return "<member><name>" + fieldName + "</name><value><" + valueType + ">" + value + "</" + valueType + "></value></member>";
    }





        /*
    private Document xmlAttribute(String id, String attributed) {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><methodCall><methodName>glpi.addTicketFollowup</methodName><params><param><value><struct>";

        xml += xmlAddField(GlpiConstants.TICKET_ID_FORM, "string", id);

        xml += xmlAddField(GlpiConstants.ASSIGNED_FORM, "string", attributed);

        xml += GlpiConstants.END_XML_FORMAT;
        return ParserTool.getParsedXml(xml);
    }
*/
}