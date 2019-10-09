package fr.openent.supportpivot.constants;

import java.util.*;

public class GlpiConstants {
    /**
     * JSON fields for GLPI format
     */
    public final static String ID_FIELD = "id";

    public final static String USERS_FIELD = "users";

    public final static String REQUESTER_FIELD = "requester";
    public final static String CREATOR_FIELD = "user_name";

    public final static String ASSIGN_FIELD = "assign";
    public final static String ATTRIBUTION_FIELD = "user_name";

    public final static String OBSERVER_FIELD = "observer";
    public final static String OBSERVER_NAME = "user_name";

    public final static String TICKETTYPE_FIELD = "type";
    public final static String TITLE_FIELD = "name";
    public final static String DESCRIPTION_FIELD = "content";
    public final static String PRIORITY_FIELD = "priority";
    public final static String MODULES_FIELD = "ticketcategories_name";


    public final static String COMM_FIELD = "followups";
    public final static String TASKS_FIELD = "tasks";
    public final static String EVENT_FIELD = "event";

    public final static String ATTACHMENT_FIELD = "documents";
    public final static String ATTACHMENT_NAME_FIELD = "filename";
    public final static String ATTACHMENT_CONTENT_FIELD = "filepath";
    public final static String STATUS_FIELD = "status";
    public final static String DATE_CREA_FIELD = "date_creation";
    public final static String DATE_RESO_FIELD = "solvedate";
    public final static String DATE_UPDATE_FIELD = "date_mod";

    public final static String ATTACHMENT_NAME_FORM = "name";
    public final static String ATTACHMENT_B64_FORM = "base64";

    public final static String JIRA_ASSIGNED_VALUE = "Support ENT";

//    public final static String GLPI_GET_TYPE_NAME = "type_name";

    public final static String HOST_URL = "http://194.199.35.14:8080";
    public final static String ROOT_URI = "/glpi/plugins/webservices/xmlrpc.php";

    public final static String NOT_AUTHENTICATED_CODE = "13";

    /**
     * Mandatory fields
     */

    public static final String PRIORITY_SMALLER = "1";
    public static final String PRIORITY_MINOR = "2";
    public static final String PRIORITY_MEDIUM = "3";
    public static final String PRIORITY_HIGH = "4";
    public static final String PRIORITY_HIGHER = "5";
    public static final String PRIORITY_MAJOR = "6";

    public static final List<String> PRIORITY_LEVEL = Arrays.asList(
            PRIORITY_SMALLER,
            PRIORITY_MINOR,
            PRIORITY_MEDIUM,
            PRIORITY_HIGH,
            PRIORITY_HIGHER,
            PRIORITY_MAJOR);

    public static final List<String> PRIORITY_LEVEL_MINOR = Arrays.asList(
            PRIORITY_SMALLER,
            PRIORITY_MINOR,
            PRIORITY_MEDIUM);

    public static final List<String> PRIORITY_LEVEL_MAJOR = Arrays.asList(
            PRIORITY_HIGH,
            PRIORITY_HIGHER,
            PRIORITY_MAJOR);

    public static final String STATUS_NEW = "1";
    public static final String STATUS_IN_PROGRESS_ATTRIBUTED = "2";
    public static final String STATUS_IN_PROGRESS_SCHEDULED = "3";
    public static final String STATUS_WAITING = "4";
    public static final String STATUS_RESOLVED = "5";
    public static final String STATUS_CLOSED = "6";

    public static final List<String> STATUS_LIST = Arrays.asList(
            STATUS_NEW,
            STATUS_IN_PROGRESS_ATTRIBUTED,
            STATUS_IN_PROGRESS_SCHEDULED,
            STATUS_WAITING,
            STATUS_RESOLVED,
            STATUS_CLOSED);

    public static final List<String> STATUS_OPENED_LIST = Arrays.asList(
            STATUS_IN_PROGRESS_ATTRIBUTED,
            STATUS_IN_PROGRESS_SCHEDULED,
            STATUS_WAITING);


    public static final String TYPE_INCIDENT = "1";
    public static final String TYPE_REQUEST = "2";

    public static final String MODULE_OTHER = "Autre";

    /*
    public static final Map <Integer, String> MODULES = new HashMap<Integer, String>()
    {
        {
            put(15,"Manuel Numérique");
            put(16,"Agenda");
            put(17,"Annuaire");
            put(18,"Autre");
            put(19,"Blog");
            put(20,"Cahier multimédia");
            put(21,"Carte mentale");
            put(22,"Documents");
            put(23,"Exercices et évaluations");
            put(24,"Export");
            put(25,"Fil de nouveautés");
            put(26,"Frise chronologique");
            put(27,"LOOL");
            put(28,"Mediacentre");
            put(29,"Messagerie");
            put(30,"Moodle");
            put(31,"Mur Collaboratif");
            put(32,"Poste-fichiers");
            put(33,"Recherche");
            put(34,"Réservation de ressource");
            put(35,"Sites web");
            put(36,"Wiki");

        }
    };
*/
    /*
    public static final Map <Integer, String> LOCATION_ID = new HashMap<Integer, String>()
    {
        {
            put(1,"");
            put(2,"");
            put(3,"");
            put(4,"");
            put(5,"");
            put(6,"");
            put(7,"");
            put(8,"");
            put(9,"");
            put(10,"");
        }
    };*/

    public final static String DESCRIPTION_CREATE = "content";
    public final static String TITLE_CREATE = "title";
    public final static String ENTITY_CREATE = "entity";
    public final static String REQUESTER_CREATE = "requester";
    public final static String TYPE_CREATE = "type";
    public final static String CATEGORY_CREATE = "category";

    public final static String ENTITY_ID = "342";
    public final static String REQUESTER_ID = "42";
    public final static String TYPE_ID = "2";

    public final static String CONTENT_COMMENT = "content";
    public final static String TICKET_ID_FORM = "ticket";

    public final static String END_XML_FORMAT = "</struct></value></param></params></methodCall>";

    public static final String ERROR_CODE_NAME = "faultCode";
    public static final String ERROR_LOGIN_CODE = "13";
}