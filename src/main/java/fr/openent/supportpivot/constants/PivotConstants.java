package fr.openent.supportpivot.constants;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PivotConstants {

    public final static String BUS_SEND = "support.update.bugtracker";
    public final static String ENT_BUS_OK_STATUS = "OK";

    /**
     * JSON fields for Pivot format
     */
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
    public final static String MODULESGLPI_FIELD = "modules_glpi";
    public final static String MODULESJIRA_FIELD = "modules_jira";

    public final static String COMM_FIELD = "commentaires";
    public final static String ATTACHMENT_FIELD = "pj";
    public final static String ATTACHMENT_NAME_FIELD = "nom";
    public final static String ATTACHMENT_CONTENT_FIELD = "contenu";
    public final static String STATUSIWS_FIELD = "statut_iws";
    public final static String STATUSENT_FIELD = "statut_ent";
    public final static String STATUSJIRA_FIELD = "statut_jira";

    public final static String DATE_CREA_FIELD = "date_creation";
    public final static String DATE_CREAGLPI_FIELD = "date_creation_glpi";
    public final static String DATE_CREAJIRA_FIELD = "date_creation_jira";

    public final static String DATE_RESOIWS_FIELD = "date_resolution_iws";
    public final static String DATE_RESO_FIELD = "date_resolution";
    public final static String DATE_RESOJIRA_FIELD = "date_resolution_jira";

    public final static String TECHNICAL_RESP_FIELD = "reponse_technique";
    public final static String CLIENT_RESP_FIELD = "reponse_client";
    public final static String ATTRIBUTION_FIELD = "attribution";

    public final static String ATTRIBUTION_IWS = "RECTORAT";
    public final static String ATTRIBUTION_ENT = "ENT";
    public final static String ATTRIBUTION_CGI = "CGI";
    public final static String ATTRIBUTION_GLPI = "GLPI";
    public final static String ATTRIBUTION_NAME = "support-CGI";

    //TODO voir si utile ici
    public final static String IDGLPI_FIELD = "id_glpi";
    public final static String STATUSGLPI_FIELD = "status_glpi";
    public final static String DATE_RESOGLPI_FIELD = "date_resolution_glpi";

    public final static String DATE_MODGLPI_FIELD = "date_modification_glpi";
    public final static String DATE_MODJIRA_FIELD = "date_modification_jira";
    public final static String DATE_MOD_FIELD = "date_modification";

    /**
     * Mandatory fields
     */
    public static final String[] IWS_MANDATORY_FIELDS = {
            IDIWS_FIELD,
            COLLECTIVITY_FIELD,
            CREATOR_FIELD,
            DESCRIPTION_FIELD,
            ATTRIBUTION_FIELD
    };

    public static final String[] ENT_MANDATORY_FIELDS = {
            ID_FIELD,
            CREATOR_FIELD,
            TITLE_FIELD,
            DESCRIPTION_FIELD
    };

    public static final String PRIORITY_MINOR = "Mineur";
    public static final String PRIORITY_MAJOR = "Majeur";
    public static final String PRIORITY_BLOCKING = "Bloquant";

    public static final List<String> PIVOT_PRIORITY_LEVEL = Arrays.asList(
            PRIORITY_MINOR,
            PRIORITY_MAJOR,
            PRIORITY_BLOCKING);

    public static final String STATUS_NEW = "Ouvert";
    public static final String STATUS_OPENED = "En cours";
    public static final String STATUS_RESOLVED = "R&eacute;solu";
    public static final String STATUS_CLOSED = "Ferm&eacute;";
//####################### TODO add in jira constants / glpi constants
    /*public static final String STATUS_NEW = "Ouvert";
    public static final String STATUS_OPENED = "En cours";
    public static final String STATUS_RESOLVED = "R&eacute;solu";
    public static final String STATUS_CLOSED = "Ferm&eacute;";*/

    public static final List<String> STATUS_LIST = Arrays.asList(
            STATUS_NEW,
            STATUS_OPENED,
            STATUS_RESOLVED,
            STATUS_CLOSED);

    public static final String STATUS_JIRA_NEW = "Nouveau";
    public static final String STATUS_JIRA_RESOLVED_TEST = "R&eacute;solu et &agrave; tester";
    public static final String STATUS_JIRA_RESOLVED = "R&eacute;solu sans livraison";
    public static final String STATUS_JIRA_TO_PROVIDE = "Test&eacute; et &agrave; livrer";
    public static final String STATUS_JIRA_TO_WAITING = "En attente";
    public static final String STATUS_JIRA_TO_CLOSED = "Ferm&eacute;e";
    public static final String STATUS_JIRA_TODO = "A faire";
    public static final String STATUS_JIRA_END = "Fini";
    public static final String STATUS_JIRA_TO_RECLAIM = "A recetter";

    public static final String TYPE_JIRA_REQUEST = "Demande";
    public static final String TYPE_JIRA_ANOMALY = "Anomalie";
    public static final String TYPE_GLPI_REQUEST = "Demande";
    public static final String TYPE_ID_GLPI_REQUEST = "2";
    public static final String TYPE_GLPI_ANOMALY = "Anomalie";
    public static final String TYPE_ID_GLPI_ANOMALY = "1";

    public static final String TYPE_INCIDENT = "1";
    public static final String TYPE_REQUEST = "5";

    //#######################
    public final static Map<String, String> APPLICATIONS_MAP = new HashMap<String, String>()
    {
        {
            put("/eliot/absences",stringEncode("Absences (Axess)"));
            put("/actualites",stringEncode("Actualit&eacute;s"));
            put("/admin",stringEncode("Administration"));
            put("/calendar",stringEncode("Agenda"));
            put("/eliot/agenda",stringEncode("Agenda (Axess)"));
            put("/support",stringEncode("Aide et Support"));
            put("/userbook/annuaire#/search",stringEncode("Annuaire"));
            put("/blog",stringEncode("Blog"));
            put("/eliot/textes",stringEncode("Cahier de textes (Axess)"));
            put("/mindmap",stringEncode("Carte mentale"));
            put("/rack",stringEncode("Casier"));
            put("/community",stringEncode("Communaut&eacute;"));
            put("/cas",stringEncode("Connexion"));
            put("/workspace/workspace",stringEncode("Documents"));
            put("/exercizer",stringEncode("Exercizer"));
            put("/forum",stringEncode("Forum"));
            put("/timelinegenerator",stringEncode("Frise chronologique"));
            put("/conversation/conversation",stringEncode("Messagerie"));
            put("/collaborativewall",stringEncode("Mur collaboratif"));
            put("/eliot/notes",stringEncode("Notes (Axess)"));
            put("/pages",stringEncode("Pages"));
            put("/rbs",stringEncode("R&eacute;servation de ressources"));
            put("/eliot/scolarite",stringEncode("Scolarité (Axess)"));
            put("/poll",stringEncode("Sondage"));
            put("/statistics",stringEncode("Statistiques"));
            put("/rss",stringEncode("Widget Rss"));
            put("/bookmark",stringEncode("Widget Signets"));
            put("/wiki",stringEncode("Wiki"));
            put("/xiti",stringEncode("Xiti"));
        }
    };


    /**
     * Encode a string in UTF-8
     * @param in String to encode
     * @return encoded String
     */
    private static String stringEncode(String in) {
        return  in;
    }
}
