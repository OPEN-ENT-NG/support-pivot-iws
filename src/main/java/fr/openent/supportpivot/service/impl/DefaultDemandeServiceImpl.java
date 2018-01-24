package fr.openent.supportpivot.service.impl;

import com.fasterxml.jackson.databind.deser.Deserializers;
import fr.openent.supportpivot.Supportpivot;
import fr.openent.supportpivot.service.DemandeService;
import fr.wseduc.mongodb.MongoDb;
import fr.wseduc.webutils.Either;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import fr.wseduc.webutils.email.EmailSender;
import org.vertx.java.core.json.impl.Json;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.platform.Container;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.apache.commons.lang3.StringEscapeUtils.escapeHtml4;
import fr.wseduc.webutils.http.Renders;

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.json.impl.Base64;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by colenot on 07/12/2017.
 *
 * Default implementation for DemandeService
 */
public class DefaultDemandeServiceImpl implements DemandeService {

    private static final String DEMANDE_COLLECTION = "support.demandes";
    private final MongoDb mongo;
    private final EmailSender emailSender;
    private final Logger log;
    private final Vertx vertx;

    private final String MAIL_IWS;
    private final String COLLECTIVITY_DEFAULT;
    private final String ATTRIBUTION_DEFAULT;
    private final String TICKETTYPE_DEFAULT;
    private final String PRIORITY_DEFAULT;
    private final String JIRA_LOGIN;
    private final String JIRA_PASSWD;
    private final String JIRA_LINK_URL;


    public DefaultDemandeServiceImpl(Vertx vertx, Container container, EmailSender emailSender) {
        this.mongo = MongoDb.getInstance();
        this.emailSender = emailSender;
        this.log = container.logger();
        this.vertx = vertx;
        this.MAIL_IWS = container.config().getString("mail-iws");
        this.COLLECTIVITY_DEFAULT = container.config().getString("default-collectivity");
        this.ATTRIBUTION_DEFAULT = container.config().getString("default-attribution");
        this.TICKETTYPE_DEFAULT = container.config().getString("default-tickettype");
        this.PRIORITY_DEFAULT = container.config().getString("default-priority");
        this.JIRA_LOGIN = container.config().getString("jira-login");
        this.JIRA_PASSWD = container.config().getString("jira-passwd");
        this.JIRA_LINK_URL = container.config().getString("jira-link");
    }

    /**
     * Add issue from IWS
     * Check every mandatory field is present in jsonPivot, then send for treatment
     * @param jsonPivot JSON object in PIVOT format
     */
    public void addIWS(HttpServerRequest request, JsonObject jsonPivot, final Handler<Either<String, JsonObject>> handler) {

        Boolean isAllMandatoryFieldsPresent = true;
        for( String field : Supportpivot.IWS_MANDATORY_FIELDS ) {
            if( ! jsonPivot.containsField(field) ) {
                isAllMandatoryFieldsPresent = false;
            }
        }
        if( ! isAllMandatoryFieldsPresent ) {
            handler.handle(new Either.Left<String, JsonObject>("2"));
        }
        else {
            add(request, jsonPivot, handler);
        }
    }

    /**
     * Add issue from ENT
     * - Check every mandatory field is present in jsonPivot, then send for treatment
     * - Replace empty values by default ones
     * - Replace modules names
     * @param jsonPivot JSON object in PIVOT format
     */
    public void addENT(JsonObject jsonPivot, final Handler<Either<String, JsonObject>> handler) {

        Boolean isAllMandatoryFieldsPresent = true;
        for( String field : Supportpivot.ENT_MANDATORY_FIELDS ) {
            if( ! jsonPivot.containsField(field) ) {
                isAllMandatoryFieldsPresent = false;
            }
        }
        if( ! isAllMandatoryFieldsPresent ) {
            handler.handle(new Either.Left<String, JsonObject>("2"));
        }
        else {
            if( !jsonPivot.containsField(Supportpivot.COLLECTIVITY_FIELD) ) {
                jsonPivot.putString(Supportpivot.COLLECTIVITY_FIELD, COLLECTIVITY_DEFAULT);
            }
            if( !jsonPivot.containsField(Supportpivot.ATTRIBUTION_FIELD) ) {
                jsonPivot.putString(Supportpivot.ATTRIBUTION_FIELD, ATTRIBUTION_DEFAULT);
            }
            if( !jsonPivot.containsField(Supportpivot.TICKETTYPE_FIELD) ) {
                jsonPivot.putString(Supportpivot.TICKETTYPE_FIELD, TICKETTYPE_DEFAULT);
            }
            if( !jsonPivot.containsField(Supportpivot.PRIORITY_FIELD) ) {
                jsonPivot.putString(Supportpivot.PRIORITY_FIELD, PRIORITY_DEFAULT);
            }
            if( jsonPivot.containsField(Supportpivot.DATE_CREA_FIELD) ) {
                String sqlDate = jsonPivot.getString(Supportpivot.DATE_CREA_FIELD);
                jsonPivot.putString(Supportpivot.DATE_CREA_FIELD, formatSqlDate(sqlDate));
            }

            JsonArray modules = jsonPivot.getArray(Supportpivot.MODULES_FIELD, new JsonArray());
            JsonArray newModules = new JsonArray();
            for(Object o : modules){
                if( o instanceof String ) {
                    String module = (String)o;
                    newModules.addString(moduleEntToPivot(module));
                }
            }
            jsonPivot.putArray(Supportpivot.MODULES_FIELD, newModules);
            add(null, jsonPivot, handler);
        }
    }

    /**
     * Get a modules name ENT-like, returns the module name PIVOT-like
     * Returns original name by default
     * @param moduleName ENT-like module name
     * @return PIVOT-like module name encoded in UTF-8
     */
    private String moduleEntToPivot(String moduleName) {
        switch(moduleName) {
            case "actualites": return new String("Actualités".getBytes(), StandardCharsets.UTF_8);
            default: return moduleName;
        }
    }

    /**
     * Format a Date from SQL-like format to dd/MM/yyyy
     * @param sqlDate date to format
     * @return The date formatted, or the original date is case of error
     */
    private String formatSqlDate(String sqlDate) {
        SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
        SimpleDateFormat output = new SimpleDateFormat("dd/MM/yyyy");
        try {
            Date dateValue = input.parse(sqlDate);
            return output.format(dateValue);
        } catch (ParseException e) {
            log.error("Supportpivot : invalid date format" + e.getMessage());
            return sqlDate;
        }
    }

    /**
     * Get a JSON in Pivot format, and send the information to the right recipient
     * If no or unknown recipient, save it to mongo
     * @param jsonPivot Json in pivot format
     */
    private void add(HttpServerRequest request, JsonObject jsonPivot, final Handler<Either<String, JsonObject>> handler) {

        if( Supportpivot.ATTRIBUTION_IWS.equals(jsonPivot.getString(Supportpivot.ATTRIBUTION_FIELD)) ) {
            sendToIWS(request, jsonPivot, handler);
        }
        else if( Supportpivot.ATTRIBUTION_CGI.equals(jsonPivot.getString(Supportpivot.ATTRIBUTION_FIELD)) ) {
            sendToCGI(request, jsonPivot, handler);
        }
        else {
            mongo.insert(DEMANDE_COLLECTION, jsonPivot, new Handler<Message<JsonObject>>() {
                @Override
                public void handle(Message<JsonObject> retourJson) {
                    JsonObject body = retourJson.body();
                    if ("ok".equals(body.getString("status"))) {
                        handler.handle(new Either.Right<String, JsonObject>(new JsonObject().putString("status", "OK")));
                    } else {
                        handler.handle(new Either.Left<String, JsonObject>("1"));
                    }
                }
            });
        }
    }

    private void sendToCGI(HttpServerRequest request, JsonObject resource, final Handler<Either<String, JsonObject>> handler) {

        final AtomicBoolean responseIsSent = new AtomicBoolean(false);
        final JsonObject jsonJiraTicket = new JsonObject();

        System.out.println("---- DEBUT QMER ----");
        System.out.println(this.JIRA_LOGIN);
        System.out.println(this.JIRA_PASSWD);
        System.out.println(this.JIRA_LINK_URL);

        // Login & Passwd Jira
        String authInfo = new StringBuilder(this.JIRA_LOGIN)
            .append(":").append(JIRA_PASSWD).toString();

        //Exemple : {"fields": {"project":{ "key": "NGMDP" },"summary": "REST test.","description": " REST API","issuetype": {"name": "Assistance" } }}
/*
        {
                "id_iws": "« Identifiant IWS »",
                "id_ent": "",
                "id_jira": "",
                "collectivite": "MDP",
                "academie": "PARIS",
                "demandeur": " Gérald Fernandez | gfernandez@yahoo.fr | 0606060606 | Victor Hugo | codeRNE ",
                "type_demande": " Assistance ",
                "titre": " Problème de blog",
                "description": " Je ne peux plus me connecter au blog.",
                "priorite": " Bloquant ",
                "modules": ["Blog"],
            "commentaires": [
            "20170108091242 | Gérome Dupond | 01/08/2017 09 :12 :42 | Est-ce que vous pouvez essayer en supprimant le billet ",
                    "20170208093324 | Gerald Fernandez | 02/08/2017 09 :33 :24 | Je viens d’essayer ça ne fonctionne toujours pas ",
                    "20170208102247 | Gérome Dupond | 02/08/2017 10 :22 :47 | Je viens de corriger, pouvez-vous essayer à nouveau ? ",
                    "20170208103212 | Gerald Fernandez | 02/08/2017 10 :32 :12 | ça ne fonctionne pas",
                    "20170208110102 | Gérome Dupond | 02/08/2017 11 :01 :02 | je transmets la demande à l’académie qui vous tiendra informée. "
	],
            "pj": [{
            "nom": "welcome.html",
                    "contenu": "PCFET0NUWVBFIGh0bWw+CjwhLS0KIH4JQ29weXJpZ2h0IMKpIFdlYlNlcnZpY2VzIHBvdXIgbCfDiWR1Y2F0aW9uLCAyMDE0CiB+CiB+IFRoaXMgZmlsZSBpcyBwYXJ0IG9mIEVOVCBDb3JlLiBFTlQgQ29yZSBpcyBhIHZlcnNhdGlsZSBFTlQgZW5naW5lIGJhc2VkIG9uIHRoZSBKVk0uCiB+CiB+IFRoaXMgcHJvZ3JhbSBpcyBmcmVlIHNvZnR3YXJlOyB5b3UgY2FuIHJlZGlzdHJpYnV0ZSBpdCBhbmQvb3IgbW9kaWZ5CiB+IGl0IHVuZGVyIHRoZSB0ZXJtcyBvZiB0aGUgR05VIEFmZmVybyBHZW5lcmFsIFB1YmxpYyBMaWNlbnNlIGFzCiB+IHB1Ymxpc2hlZCBieSB0aGUgRnJlZSBTb2Z0d2FyZSBGb3VuZGF0aW9uICh2ZXJzaW9uIDMgb2YgdGhlIExpY2Vuc2UpLgogfgogfiBGb3IgdGhlIHNha2Ugb2YgZXhwbGFuYXRpb24sIGFueSBtb2R1bGUgdGhhdCBjb21tdW5pY2F0ZSBvdmVyIG5hdGl2ZQogfiBXZWIgcHJvdG9jb2xzLCBzdWNoIGFzIEhUVFAsIHdpdGggRU5UIENvcmUgaXMgb3V0c2lkZSB0aGUgc2NvcGUgb2YgdGhpcwogfiBsaWNlbnNlIGFuZCBjb3VsZCBiZSBsaWNlbnNlIHVuZGVyIGl0cyBvd24gdGVybXMuIFRoaXMgaXMgbWVyZWx5IGNvbnNpZGVyZWQKIH4gbm9ybWFsIHVzZSBvZiBFTlQgQ29yZSwgYW5kIGRvZXMgbm90IGZhbGwgdW5kZXIgdGhlIGhlYWRpbmcgb2YgImNvdmVyZWQgd29yayIuCiB+CiB+IFRoaXMgcHJvZ3JhbSBpcyBkaXN0cmlidXRlZCBpbiB0aGUgaG9wZSB0aGF0IGl0IHdpbGwgYmUgdXNlZnVsLAogfiBidXQgV0lUSE9VVCBBTlkgV0FSUkFOVFk7IHdpdGhvdXQgZXZlbiB0aGUgaW1wbGllZCB3YXJyYW50eSBvZgogfiBNRVJDSEFOVEFCSUxJVFkgb3IgRklUTkVTUyBGT1IgQSBQQVJUSUNVTEFSIFBVUlBPU0UuCiB+CiAtLT4KCjxodG1sPgo8aGVhZCBsYW5nPSJlbiI+Cgk8bWV0YSBjaGFyc2V0PSJVVEYtOCI+Cgk8bWV0YSBuYW1lPSJ2aWV3cG9ydCIgY29udGVudD0id2lkdGg9ZGV2aWNlLXdpZHRoLCBpbml0aWFsLXNjYWxlPTEsIG1heGltdW0tc2NhbGU9MSwgdXNlci1zY2FsYWJsZT1ubyIgLz4KCgk8dGl0bGU+TWVzIGFwcGxpczwvdGl0bGU+Cgk8c2NyaXB0IHR5cGU9InRleHQvamF2YXNjcmlwdCI+CgkJdmFyIGFwcFByZWZpeCA9ICcuJzsKCTwvc2NyaXB0PgoJPHNjcmlwdCB0eXBlPSJ0ZXh0L2phdmFzY3JpcHQiIHNyYz0iL3B1YmxpYy9kaXN0L2VudGNvcmUvbmctYXBwLWM4YjlhMDM1OGMuanMiIGlkPSJjb250ZXh0Ij48L3NjcmlwdD4KCTxzY3JpcHQgdHlwZT0idGV4dC9qYXZhc2NyaXB0IiBzcmM9Ii9wdWJsaWMvZGlzdC9teWFwcHMvYXBwbGljYXRpb24tZjZmMzY5ODJmMi5qcyI+PC9zY3JpcHQ+CjwvaGVhZD4KPGJvZHkgbmctY29udHJvbGxlcj0iQXBwbGljYXRpb25Db250cm9sbGVyIj4KCTxwb3J0YWw+CgkJPGRpdiBjbGFzcz0icm93IiBuZy1pbmNsdWRlPSJ0ZW1wbGF0ZS5jb250YWluZXJzLm1haW4iPjwvZGl2PgoJPC9wb3J0YWw+CjwvYm9keT4KPC9odG1sPgo="
        },
            {
                "nom": "bye.html",
                    "contenu": "PCFET0NUWVBFIGh0bWw+CjwhLS0KIH4JQ29weXJpZ2h0IMKpIFdlYlNlcnZpY2VzIHBvdXIgbCfDiWR1Y2F0aW9uLCAyMDE0CiB+CiB+IFRoaXMgZmlsZSBpcyBwYXJ0IG9mIEVOVCBDb3JlLiBFTlQgQ29yZSBpcyBhIHZlcnNhdGlsZSBFTlQgZW5naW5lIGJhc2VkIG9uIHRoZSBKVk0uCiB+CiB+IFRoaXMgcHJvZ3JhbSBpcyBmcmVlIHNvZnR3YXJlOyB5b3UgY2FuIHJlZGlzdHJpYnV0ZSBpdCBhbmQvb3IgbW9kaWZ5CiB+IGl0IHVuZGVyIHRoZSB0ZXJtcyBvZiB0aGUgR05VIEFmZmVybyBHZW5lcmFsIFB1YmxpYyBMaWNlbnNlIGFzCiB+IHB1Ymxpc2hlZCBieSB0aGUgRnJlZSBTb2Z0d2FyZSBGb3VuZGF0aW9uICh2ZXJzaW9uIDMgb2YgdGhlIExpY2Vuc2UpLgogfgogfiBGb3IgdGhlIHNha2Ugb2YgZXhwbGFuYXRpb24sIGFueSBtb2R1bGUgdGhhdCBjb21tdW5pY2F0ZSBvdmVyIG5hdGl2ZQogfiBXZWIgcHJvdG9jb2xzLCBzdWNoIGFzIEhUVFAsIHdpdGggRU5UIENvcmUgaXMgb3V0c2lkZSB0aGUgc2NvcGUgb2YgdGhpcwogfiBsaWNlbnNlIGFuZCBjb3VsZCBiZSBsaWNlbnNlIHVuZGVyIGl0cyBvd24gdGVybXMuIFRoaXMgaXMgbWVyZWx5IGNvbnNpZGVyZWQKIH4gbm9ybWFsIHVzZSBvZiBFTlQgQ29yZSwgYW5kIGRvZXMgbm90IGZhbGwgdW5kZXIgdGhlIGhlYWRpbmcgb2YgImNvdmVyZWQgd29yayIuCiB+CiB+IFRoaXMgcHJvZ3JhbSBpcyBkaXN0cmlidXRlZCBpbiB0aGUgaG9wZSB0aGF0IGl0IHdpbGwgYmUgdXNlZnVsLAogfiBidXQgV0lUSE9VVCBBTlkgV0FSUkFOVFk7IHdpdGhvdXQgZXZlbiB0aGUgaW1wbGllZCB3YXJyYW50eSBvZgogfiBNRVJDSEFOVEFCSUxJVFkgb3IgRklUTkVTUyBGT1IgQSBQQVJUSUNVTEFSIFBVUlBPU0UuCiB+CiAtLT4KCjxodG1sPgo8aGVhZCBsYW5nPSJlbiI+Cgk8bWV0YSBjaGFyc2V0PSJVVEYtOCI+Cgk8bWV0YSBuYW1lPSJ2aWV3cG9ydCIgY29udGVudD0id2lkdGg9ZGV2aWNlLXdpZHRoLCBpbml0aWFsLXNjYWxlPTEsIG1heGltdW0tc2NhbGU9MSwgdXNlci1zY2FsYWJsZT1ubyIgLz4KCgk8dGl0bGU+TWVzIGFwcGxpczwvdGl0bGU+Cgk8c2NyaXB0IHR5cGU9InRleHQvamF2YXNjcmlwdCI+CgkJdmFyIGFwcFByZWZpeCA9ICcuJzsKCTwvc2NyaXB0PgoJPHNjcmlwdCB0eXBlPSJ0ZXh0L2phdmFzY3JpcHQiIHNyYz0iL3B1YmxpYy9kaXN0L2VudGNvcmUvbmctYXBwLWM4YjlhMDM1OGMuanMiIGlkPSJjb250ZXh0Ij48L3NjcmlwdD4KCTxzY3JpcHQgdHlwZT0idGV4dC9qYXZhc2NyaXB0IiBzcmM9Ii9wdWJsaWMvZGlzdC9teWFwcHMvYXBwbGljYXRpb24tZjZmMzY5ODJmMi5qcyI+PC9zY3JpcHQ+CjwvaGVhZD4KPGJvZHkgbmctY29udHJvbGxlcj0iQXBwbGljYXRpb25Db250cm9sbGVyIj4KCTxwb3J0YWw+CgkJPGRpdiBjbGFzcz0icm93IiBuZy1pbmNsdWRlPSJ0ZW1wbGF0ZS5jb250YWluZXJzLm1haW4iPjwvZGl2PgoJPC9wb3J0YWw+CjwvYm9keT4KPC9odG1sPgo="
            }
],
            "statut_iws": "En attente",
                "statut_ent": "En attente",
                "statut_jira": "En attente",
                "date_creation": "16/11/2017 09:32:34",
                "date_resolution_iws": "",
                "date_resolution_ent": "",
                "date_resolution_jira": "",
                "reponse_technique": "",
                "reponse_client": "",
                "attribution": "RECTORAT"
        }
        */



        jsonJiraTicket.putObject("fields", new JsonObject()
            .putObject("project", new JsonObject()
                    .putString("key", resource.getString(Supportpivot.COLLECTIVITY_FIELD)))
            .putString("summary", resource.getString(Supportpivot.TITLE_FIELD))
            .putString("description", resource.getString(Supportpivot.DESCRIPTION_FIELD))
            .putObject("issuetype", new JsonObject()
                    .putString("name", resource.getString(Supportpivot.TICKETTYPE_FIELD)))
            //.putObject("assignee", new JsonObject()
            //    .putString("name", resource.getString(Supportpivot.TICKETTYPE_FIELD)))
            //.putObject("labels", new JsonObject()
            //        .putArray(resource.getString(Supportpivot.MODULES_FIELD)))
            .putObject("priority", new JsonObject()
                    .putString("name", resource.getString(Supportpivot.PRIORITY_FIELD))));


        final JsonArray jsonJiraComments = resource.getArray(Supportpivot.COMM_FIELD);


        log.info(jsonJiraTicket);

        // Create ticket via Jira API

        URI jira_URI = null;
        try {
            jira_URI = new URI(JIRA_LINK_URL);
            } catch (URISyntaxException e) {
                log.error("Invalid jira web service uri", e);
                //renderError(request);
            }

        if (jira_URI != null) {
            final HttpClient httpClient = generateHttpClient(jira_URI);

            System.out.println("On ouvre");
            System.out.println(jira_URI);

            //param du proxy todo
            // tester avec le web service en GET avec login et mdp : OK
            // tester apres aves le POST  : OK
            // faire un json avec le JSON object et surtout pas en stringbuilder : OK
            // si 302 alors faire suite ...

            final HttpClientRequest httpClientRequest = httpClient.post(JIRA_LINK_URL  , new Handler<HttpClientResponse>() {
                @Override
                public void handle(HttpClientResponse response) {

                    System.out.println("On test le status :" + response.statusCode());
                    int status = response.statusCode();


                    // Si le ticket a bien été créé dans JIRA
                    // HTTP Status Code 201: The request has been fulfilled and has resulted in one or more new resources being created.
                    if (response.statusCode() == 201) {
                        System.out.println("Test OK status 201 !");

                        //Récupère le retour
                        response.bodyHandler(new Handler<Buffer>() {
                            @Override
                            public void handle(Buffer buffer) {
                                //Affiche le retour
                                JsonObject infoNewJiraTicket = new JsonObject(buffer.toString());
                                String idNewJiraTicket = infoNewJiraTicket.getString("key");

                                log.info("JIRA ticket Informations : " + infoNewJiraTicket);
                                log.info("JIRA ticket ID created : " + idNewJiraTicket);

                                log.info("JIRA comments : " + jsonJiraComments);
                                //Queue<String> commentsQueue = new LinkedList<String>();
                                LinkedList<String> commentsLinkedList = new LinkedList<String>();

                                for(int i=0 ; i < jsonJiraComments.size() ; i++){
                                    commentsLinkedList.add(jsonJiraComments.get(i).toString());
                                    //mail.append("<br />commentaires = ")
                                    //        .append(escapeHtml4((String)comm.get(i)));
                                }

                                sendJiraComments(idNewJiraTicket, commentsLinkedList, handler);

                                //Faire une boucle pour créer les commentaires provenant du JSON, si il y a
                                // test si champs commentaire vide
                                // si non vide alors boucle par commentaire pour créer
                                // voir si ils existent deja
                                log.error("TOOTOO");

                            }
                        });

                        //Get le number du ticket et ajouter chaque commentaires
                        //http://10.83.199.17:8081/rest/api/2/issue/NGMDP-34/comment
                        //{
                        //    "body": "Lorem ipsum augue semper."
                        //}

                    } else {
                        log.error("Error when calling URL : " + response.statusMessage());
                        //renderError(request);
                    }

                }
            });

            httpClientRequest.putHeader("Authorization", "Basic " + Base64.encodeBytes(authInfo.getBytes()));
            httpClientRequest.putHeader("Content-Type", "application/json");
            httpClientRequest.setChunked(true);
            httpClientRequest.write(jsonJiraTicket.encode());

            if (!responseIsSent.getAndSet(true)) {
                httpClient.close();
                log.info("On close");
            }

            httpClientRequest.end();
            log.info("On end");
        }

        log.info("---- FIN QMER ----");

        //curl -D- -u "ISILOG":"isilog_17" -X POST -H "Content-Type: application/json" --data '{ "fields": {"project":{ "key": "NGMDP" },"summary": "REST test.","description": " REST API","issuetype": {"name": "Assistance" } }}' https://jira-preprod.gdapublic.fr/rest/api/2/issue/

        /* retour requete :
            HTTP/1.1 201
            Date: Mon, 15 Jan 2018 13:40:15 GMT
            Server: Apache/2.4.25 (Red Hat) OpenSSL/1.0.1e-fips
            X-AREQUESTID: 880x34463x1
            X-ASEN: SEN-2025063
            X-Seraph-LoginReason: OK
            X-ASESSIONID: 14ltxrt
            X-AUSERNAME: ISILOG
            Cache-Control: no-cache, no-store, no-transform
            X-Content-Type-Options: nosniff
            Content-Type: application/json;charset=UTF-8
            Set-Cookie: JSESSIONID=3A68B6049CC24AEC490EC04DD408F27C; Path=/; Secure; HttpOnly
            Set-Cookie: atlassian.xsrf.token=" BJB4-PTJ7-E367-MKGC |f13243265075d94f2fc73df272ec52570a4f3e74|lin"; Version=1; Path=/; Secure
            Transfer-Encoding: chunked

            {"id":"13016","key":"NGMDP-24","self":"https://jira-preprod.gdapublic.fr/rest/api/2/issue/13016"}
         */

    }

    /**
     * Send pivot information to IWS -- by mail
     * Every data must be htmlEncoded because of encoding / mails incompatibility
     * @param jsonPivot JSON in pivot format
     */
    private void sendToIWS(HttpServerRequest request, JsonObject jsonPivot, final Handler<Either<String, JsonObject>> handler) {

        StringBuilder mail = new StringBuilder()
            .append("collectivite = ")
            .append(escapeHtml4(jsonPivot.getString(Supportpivot.COLLECTIVITY_FIELD)))
            .append("<br />academie = ")
            .append(escapeHtml4(jsonPivot.getString(Supportpivot.ACADEMY_FIELD, "")))
            .append("<br />demandeur = ")
            .append(escapeHtml4(jsonPivot.getString(Supportpivot.CREATOR_FIELD)))
            .append("<br />type_demande = ")
            .append(escapeHtml4(jsonPivot.getString(Supportpivot.TICKETTYPE_FIELD, "")))
            .append("<br />titre = ")
            .append(escapeHtml4(jsonPivot.getString(Supportpivot.TITLE_FIELD)))
            .append("<br />description = ")
            .append(escapeHtml4(jsonPivot.getString(Supportpivot.DESCRIPTION_FIELD)))
            .append("<br />priorite = ")
            .append(escapeHtml4(jsonPivot.getString(Supportpivot.PRIORITY_FIELD, "")))
            .append("<br />id_jira = ")
            .append(escapeHtml4(jsonPivot.getString(Supportpivot.IDJIRA_FIELD, "")))
            .append("<br />id_ent = ")
            .append(escapeHtml4(jsonPivot.getString(Supportpivot.IDENT_FIELD)))
            .append("<br />id_iws = ")
            .append(escapeHtml4(jsonPivot.getString(Supportpivot.IDIWS_FIELD, "")));

        JsonArray comm = jsonPivot.getArray(Supportpivot.COMM_FIELD, new JsonArray());
        for(int i=0 ; i<comm.size();i++){
            mail.append("<br />commentaires = ")
                    .append(escapeHtml4((String)comm.get(i)));
        }

        JsonArray modules =   jsonPivot.getArray(Supportpivot.MODULES_FIELD, new JsonArray());
        mail.append("<br />modules = ");
        for(int i=0 ; i<modules.size();i++){
            if(i > 0) {
                mail.append(", ");
            }
            mail.append(escapeHtml4((String)modules.get(i)));
        }
        mail.append("<br />statut_iws = ")
            .append(escapeHtml4(jsonPivot.getString(Supportpivot.STATUSIWS_FIELD, "")))
            .append("<br />statut_ent = ")
            .append(escapeHtml4(jsonPivot.getString(Supportpivot.STATUSENT_FIELD, "")))
            .append("<br />statut_jira = ")
            .append(escapeHtml4(jsonPivot.getString(Supportpivot.STATUSJIRA_FIELD, "")))
            .append("<br />date_creation = ")
            .append(escapeHtml4(jsonPivot.getString(Supportpivot.DATE_CREA_FIELD, "")))
            .append("<br />date_resolution_iws = ")
            .append(escapeHtml4(jsonPivot.getString(Supportpivot.DATE_RESOIWS_FIELD, "")))
            .append("<br />date_resolution_ent = ")
            .append(escapeHtml4(jsonPivot.getString(Supportpivot.DATE_RESOENT_FIELD, "")))
            .append("<br />date_resolution_jira = ")
            .append(escapeHtml4(jsonPivot.getString(Supportpivot.DATE_RESOJIRA_FIELD, "")))
            .append("<br />reponse_technique = ")
            .append(escapeHtml4(jsonPivot.getString(Supportpivot.TECHNICAL_RESP_FIELD, "")))
            .append("<br />reponse_client = ")
            .append(escapeHtml4(jsonPivot.getString(Supportpivot.CLIENT_RESP_FIELD, "")))
            .append("<br />attribution = ")
            .append(escapeHtml4(jsonPivot.getString(Supportpivot.ATTRIBUTION_FIELD)));

        String mailTo = jsonPivot.getString("email");
        if( mailTo == null || mailTo.isEmpty() ) {
            mailTo = this.MAIL_IWS;
        }

        emailSender.sendEmail(request,
                mailTo,
                null,
                null,
                "TICKETCGI",
                mail.toString(),
                null,
                false,
                new Handler<Message<JsonObject>>() {
                    @Override
                    public void handle(Message<JsonObject> jsonObjectMessage) {
                        handler.handle(new Either.Right<String, JsonObject>(jsonObjectMessage.body()));
                    }
                });
    }

    /**
     * Send an issue to IWS with fictive info, for testing purpose
     * @param mailTo mail to send to
     */
    @Override
    public void testMailToIWS(HttpServerRequest request, String mailTo, Handler<Either<String, JsonObject>> handler) {

        JsonObject resource = new JsonObject()
                .putString(Supportpivot.COLLECTIVITY_FIELD, "MDP")
                .putString(Supportpivot.ACADEMY_FIELD, "Paris")
                .putString(Supportpivot.CREATOR_FIELD, "Jean Dupont")
                .putString(Supportpivot.TICKETTYPE_FIELD, "Assistance")
                .putString(Supportpivot.TITLE_FIELD, stringEncode("Demande générique de test"))
                .putString(Supportpivot.DESCRIPTION_FIELD, stringEncode("Demande afin de tester la création de demande vers IWS"))
                .putString(Supportpivot.PRIORITY_FIELD, "Mineure")
                .putArray(Supportpivot.MODULES_FIELD,
                        new JsonArray().addString(stringEncode("Actualités")).addString("Assistance ENT"))
                .putString(Supportpivot.IDENT_FIELD, "42")
                .putArray(Supportpivot.COMM_FIELD, new JsonArray()
                    .addString("Jean Dupont| 17/11/2071 | La correction n'est pas urgente.")
                    .addString(stringEncode("Administrateur Etab | 10/01/2017 | La demande a été transmise")))
                .putString(Supportpivot.STATUSENT_FIELD, "Nouveau")
                .putString(Supportpivot.DATE_CREA_FIELD, "16/11/2017")
                .putString(Supportpivot.ATTRIBUTION_FIELD, "IWS")
                .putString("email", mailTo);

        sendToIWS(request, resource, handler);
    }

    /**
     * Encode a string in UTF-8
     * @param in String to encode
     * @return encoded String
     */
    private String stringEncode(String in) {
        return new String(in.getBytes(), StandardCharsets.UTF_8);
    }


    /**
     * Generate HTTP client
     * @param uri uri
     * @return Http client
     */
    private HttpClient generateHttpClient(URI uri) {
        return vertx.createHttpClient()
                .setHost(uri.getHost())
                .setPort((uri.getPort() > 0) ? uri.getPort() : ("https".equals(uri.getScheme()) ? 443 : 80))
                .setVerifyHost(false)
                .setTrustAll(true)
                .setSSL("https".equals(uri.getScheme()))
                .setKeepAlive(false);
    }

    /**
     * Send Jira Comments
     * @param idJira arrayComments
     */
    private void sendJiraComments(String idJira, LinkedList commentsLinkedList, Handler handler) {
        final AtomicBoolean responseIsSent = new AtomicBoolean(false);
        //test
        //JsonArray commentsTicket = arrayComments.getArray("commentaires");
        //log.info("JIRA comments 2 : " + arrayComments);

        //while comment linked list size >0 ... faire envoi dans JIRA sinon echo vide
        while( commentsLinkedList.size() > 0 ) {
            log.info(commentsLinkedList.size());
            log.info(commentsLinkedList.getFirst());


            // Login & Passwd Jira
            String authInfo = new StringBuilder(this.JIRA_LOGIN)
                    .append(":").append(JIRA_PASSWD).toString();



            URI jira_add_comment_URI = null;
            try {
                final String JIRA_LINK_URL_NEW_TICKET = new StringBuilder(this.JIRA_LINK_URL)
                        .append(idJira)
                        .append("/comment").toString();
                jira_add_comment_URI = new URI(JIRA_LINK_URL_NEW_TICKET);
            } catch (URISyntaxException e) {
                log.error("Invalid jira web service uri", e);
                //renderError(request);
            }

            if (jira_add_comment_URI != null) {
                final HttpClient httpClient = generateHttpClient(jira_add_comment_URI);

                final HttpClientRequest httpClientRequest = httpClient.post(JIRA_LINK_URL  , new Handler<HttpClientResponse>() {
                    @Override
                    public void handle(HttpClientResponse response) {

                        System.out.println("On test le status :" + response.statusCode());
                        int status = response.statusCode();


                        // Si le ticket a bien été créé dans JIRA
                        // HTTP Status Code 201: The request has been fulfilled and has resulted in one or more new resources being created.
                        if (response.statusCode() == 201) {
                            System.out.println("Test OK status 201 !");

                            //Récupère le retour
                            response.bodyHandler(new Handler<Buffer>() {
                                @Override
                                public void handle(Buffer buffer) {
                                    //Affiche le retour
                                    JsonObject infoNewJiraTicket = new JsonObject(buffer.toString());

                                    log.info("JIRA ticket Informations : " + infoNewJiraTicket);

                                    log.error("TOOTOO");

                                }
                            });

                            //Get le number du ticket et ajouter chaque commentaires
                            //http://10.83.199.17:8081/rest/api/2/issue/NGMDP-34/comment
                            //{
                            //    "body": "Lorem ipsum augue semper."
                            //}

                        } else {
                            log.error("Error when calling URL : " + response.statusMessage());
                            //renderError(request);
                        }

                    }
                });

                httpClientRequest.putHeader("Authorization", "Basic " + Base64.encodeBytes(authInfo.getBytes()));
                httpClientRequest.putHeader("Content-Type", "application/json");
                httpClientRequest.setChunked(true);

                String comment = commentsLinkedList.getFirst().toString();
                httpClientRequest.write(comment);

                if (!responseIsSent.getAndSet(true)) {
                    httpClient.close();
                    log.info("On close");
                }

                httpClientRequest.end();
                log.info("On end");
            }

            }




            commentsLinkedList.removeFirst();
            sendJiraComments(idJira, commentsLinkedList, handler);
        }

    }

}
