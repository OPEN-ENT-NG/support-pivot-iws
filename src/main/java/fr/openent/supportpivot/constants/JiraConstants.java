package fr.openent.supportpivot.constants;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JiraConstants {
    /**
     * JSON fields for Jira format
     */
    public final static String FIELDS = "fields";
    public final static String PROJECT = "project";
    public final static String PROJECT_KEY = "key";

    public final static String IDIWS_FIELD = "id_iws";
    public final static String IDENT_FIELD = "id_ent";
    public final static String IDJIRA_FIELD = "id_jira";
    public final static String IDGLPI_FIELD = "id_glpi";
    public final static String COLLECTIVITY_FIELD = "collectivite";
    public final static String ACADEMY_FIELD = "academie";
    public final static String CREATOR_FIELD = "demandeur";
    public final static String TICKETTYPE_FIELD = "type_demande";
    public final static String TITLE_FIELD = "summary";
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


    public static final String PRIORITY_MINOR_FR = "Mineure";
    public static final String PRIORITY_MINOR_EN = "Lowest";
    public static final String PRIORITY_MAJOR_FR = "Majeure";
    public static final String PRIORITY_MAJOR_EN = "High";
    public static final String PRIORITY_BLOCKING_FR = "Bloquante";
    public static final String PRIORITY_BLOCKING_EN = "Highest";

    public static final List<String> PIVOT_PRIORITY_LEVEL = Arrays.asList(
            PRIORITY_MINOR_FR,
            PRIORITY_MINOR_EN,
            PRIORITY_MAJOR_FR,
            PRIORITY_MAJOR_EN,
            PRIORITY_BLOCKING_FR,
            PRIORITY_BLOCKING_EN
    );

    public static final String STATUS_NEW = "Nouveau";
    public static final String STATUS_RESOLVED_TEST = "R&eacute;solu et &agrave; tester";
    public static final String STATUS_RESOLVED = "R&eacute;solu sans livraison";
    public static final String STATUS_TO_PROVIDE = "Test&eacute; et &agrave; livrer";
    public static final String STATUS_TO_WAITING = "En attente";
    public static final String STATUS_TO_CLOSED = "Ferm&eacute;e";
    public static final String STATUS_TODO = "A faire";
    public static final String STATUS_END = "Fini";
    public static final String STATUS_TO_RECLAIM = "A recetter";

    public static final String TYPE_REQUEST = "Demande";
    public static final String TYPE_ANOMALY = "Anomalie";

    public static final String GLPI_CUSTOM_FIELD = "12700";

    public final static Map<String, String> APPLICATIONS_MAP = new HashMap<String, String>()
    {
        {
            put("/eliot/absences","Absences (Axess)");
            put("/actualites","Actualit&eacute;s");
            put("/admin","Administration");
            put("/calendar","Agenda");
            put("/eliot/agenda","Agenda (Axess)");
            put("/support","Aide et Support");
            put("/userbook/annuaire#/search","Annuaire");
            put("/blog","Blog");
            put("/eliot/textes","Cahier de textes (Axess)");
            put("/mindmap","Carte mentale");
            put("/rack","Casier");
            put("/community","Communaut&eacute;");
            put("/cas","Connexion");
            put("/workspace/workspace","Documents");
            put("/exercizer","Exercizer");
            put("/forum","Forum");
            put("/timelinegenerator","Frise chronologique");
            put("/conversation/conversation","Messagerie");
            put("/collaborativewall","Mur collaboratif");
            put("/eliot/notes","Notes (Axess)");
            put("/pages","Pages");
            put("/rbs","R&eacute;servation de ressources");
            put("/eliot/scolarite","Scolarité (Axess)");
            put("/poll","Sondage");
            put("/statistics","Statistiques");
            put("/rss","Widget Rss");
            put("/bookmark","Widget Signets");
            put("/wiki","Wiki");
            put("/xiti","Xiti");
        }
    };
}
