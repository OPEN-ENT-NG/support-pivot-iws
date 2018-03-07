package fr.openent.supportpivot;

import org.entcore.common.http.BaseServer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class Supportpivot extends BaseServer {

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

    public final static String ATTRIBUTION_IWS = "IWS";
    public final static String ATTRIBUTION_ENT = "ENT";
    public final static String ATTRIBUTION_CGI = "CGI";
    /**
     * Mandatory fields
     */
    public static final String[] IWS_MANDATORY_FIELDS = {
            IDIWS_FIELD,
            COLLECTIVITY_FIELD,
            CREATOR_FIELD,
            TITLE_FIELD,
            DESCRIPTION_FIELD,
            ATTRIBUTION_FIELD
            };

    public static final String[] ENT_MANDATORY_FIELDS = {
            IDENT_FIELD,
            CREATOR_FIELD,
            TITLE_FIELD,
            DESCRIPTION_FIELD
    };


    public final static Map<String, String> applicationsMap = new HashMap<String, String>()
    {
        {
            put("/eliot/absences","Absences (Axess)");
            put("/actualites","Actualités");
            put("/admin","Administration");
            put("/calendar","Agenda");
            put("/eliot/agenda","Agenda (Axess)");
            put("/support","Aide et Support");
            put("/userbook/annuaire#/search","Annuaire");
            put("/blog","Blog");
            put("/eliot/textes","Cahier de textes (Axess)");
            put("/mindmap","Carte mentale");
            put("/rack","Casier");
            put("/community","Communauté");
            put("/cas","Connexion");
            put("/workspace/workspace","Documents");
            put("/exercizer","Exercizer");
            put("/forum","Forum");
            put("/timelinegenerator","Frise chronologique");
            put("/conversation/conversation","Messagerie");
            put("/collaborativewall","Mur collaboratif");
            put("/eliot/notes","Notes (Axess)");
            put("/pages","Pages");
            put("/rbs","Réservation de ressources");
            put("/eliot/scolarite","Scolarité (Axess)");
            put("/poll","Sondage");
            put("/statistics","Statistiques");
            put("/rss","Widget Rss");
            put("/bookmark","Widget Signets");
            put("/wiki","Wiki");
            put("/xiti","Xiti");
        }
    };

    @Override
	public void start() {
		super.start();
		addController(new SupportController());
	}

}
