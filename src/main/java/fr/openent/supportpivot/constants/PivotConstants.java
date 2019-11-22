package fr.openent.supportpivot.constants;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PivotConstants {

    public final static String BUS_SEND = "support.update.bugtracker";
    public final static String ENT_BUS_OK_STATUS = "OK";

    public final static String ATTRIBUTION_IWS = "RECTORAT";
    public final static String ATTRIBUTION_ENT = "ENT";
    public final static String ATTRIBUTION_CGI = "CGI";
    public final static String ATTRIBUTION_GLPI = "GLPI";


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

    public static final List<String> STATUS_LIST = Arrays.asList(
            STATUS_NEW,
            STATUS_OPENED,
            STATUS_RESOLVED,
            STATUS_CLOSED);

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
