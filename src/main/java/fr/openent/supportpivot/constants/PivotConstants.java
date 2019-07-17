package fr.openent.supportpivot.constants;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PivotConstants {
    /**
     * JSON fields for Pivot format
     */
    public final static String IDIWS_FIELD = "id_iws";
    public final static String IDENT_FIELD = "id_ent";
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
    public final static String STATUSIWS_FIELD = "statut_iws";
    public final static String STATUSENT_FIELD = "statut_ent";
    public final static String STATUSJIRA_FIELD = "statut_jira";
    public final static String DATE_CREA_FIELD = "date_creation";
    public final static String DATE_RESOIWS_FIELD = "date_resolution_iws";
    public final static String DATE_RESOENT_FIELD = "date_resolution_ent";
    public final static String DATE_RESOJIRA_FIELD = "date_resolution_jira";
    public final static String TECHNICAL_RESP_FIELD = "reponse_technique";
    public final static String CLIENT_RESP_FIELD = "reponse_client";
    public final static String ATTRIBUTION_FIELD = "attribution";

    public final static String ATTRIBUTION_IWS = "RECTORAT";
    public final static String ATTRIBUTION_ENT = "ENT";
    public final static String ATTRIBUTION_CGI = "CGI";

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
            IDENT_FIELD,
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

    public static final String STATUSENT_NEW = "1";
    public static final String STATUSENT_OPENED = "2";
    public static final String STATUSENT_RESOLVED = "3";
    public static final String STATUSENT_CLOSED = "4";

    public static final String STATUSPIVOT_NEW = stringEncode("Ouvert");
    public static final String STATUSPIVOT_OPENED = stringEncode("En cours");
    public static final String STATUSPIVOT_RESOLVED = stringEncode("R&eacute;solu");
    public static final String STATUSPIVOT_CLOSED = stringEncode("Ferm&eacute;");

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
