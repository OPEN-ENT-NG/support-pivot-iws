package fr.openent.supportpivot.service.impl;

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
import org.vertx.java.core.logging.Logger;
import org.vertx.java.platform.Container;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

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

    public void addIWS(HttpServerRequest request, JsonObject resource, final Handler<Either<String, JsonObject>> handler) {

        // Check every field is present
        Boolean isAllMandatoryFieldsPresent = true;
        for( String field : Supportpivot.IWS_MANDATORY_FIELDS ) {
            if( ! resource.containsField(field) ) {
                isAllMandatoryFieldsPresent = false;
            }
        }
        if( ! isAllMandatoryFieldsPresent ) {
            handler.handle(new Either.Left<String, JsonObject>("2"));
        }
        else {
            add(request, resource, handler);
        }

    }

    public void addENT(HttpServerRequest request, JsonObject resource, final Handler<Either<String, JsonObject>> handler) {

        // Check every field is present
        Boolean isAllMandatoryFieldsPresent = true;
        for( String field : Supportpivot.ENT_MANDATORY_FIELDS ) {
            if( ! resource.containsField(field) ) {
                isAllMandatoryFieldsPresent = false;
            }
        }
        if( ! isAllMandatoryFieldsPresent ) {
            handler.handle(new Either.Left<String, JsonObject>("2"));
        }
        else {
            if( !resource.containsField(Supportpivot.COLLECTIVITY_FIELD) ) {
                resource.putString(Supportpivot.COLLECTIVITY_FIELD, COLLECTIVITY_DEFAULT);
            }
            if( !resource.containsField(Supportpivot.ATTRIBUTION_FIELD) ) {
                resource.putString(Supportpivot.ATTRIBUTION_FIELD, ATTRIBUTION_DEFAULT);
            }
            if( !resource.containsField(Supportpivot.TICKETTYPE_FIELD) ) {
                resource.putString(Supportpivot.TICKETTYPE_FIELD, TICKETTYPE_DEFAULT);
            }
            if( !resource.containsField(Supportpivot.PRIORITY_FIELD) ) {
                resource.putString(Supportpivot.PRIORITY_FIELD, PRIORITY_DEFAULT);
            }
            if( resource.containsField(Supportpivot.DATE_CREA_FIELD) ) {
                String sqlDate = resource.getString(Supportpivot.DATE_CREA_FIELD);
                resource.putString(Supportpivot.DATE_CREA_FIELD, formatSqlDate(sqlDate));
            }
            add(request, resource, handler);
        }

    }

    private String formatSqlDate(String sqlDate) {
        SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");
        SimpleDateFormat output = new SimpleDateFormat("dd/MM/yyyy");
        try {
            Date dateValue = input.parse(sqlDate);
            return output.format(dateValue);
        } catch (ParseException e) {
            log.error("Supportpivot : invalid date format" + e.getMessage());
            return "";
        }
    }


    @Override
    public void add(HttpServerRequest request, JsonObject resource, final Handler<Either<String, JsonObject>> handler) {

        if( Supportpivot.ATTRIBUTION_IWS.equals(resource.getString(Supportpivot.ATTRIBUTION_FIELD)) ) {
            sendToIWS(request, resource, handler);
        }
        else if( Supportpivot.ATTRIBUTION_CGI.equals(resource.getString(Supportpivot.ATTRIBUTION_FIELD)) ) {
            sendToCGI(request, resource, handler);
        }
        else {
            // Insert data into mongodb
            mongo.insert(DEMANDE_COLLECTION, resource, new Handler<Message<JsonObject>>() {
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

    @Override
    public void sendToCGI(HttpServerRequest request, JsonObject resource, final Handler<Either<String, JsonObject>> handler) {

        final AtomicBoolean responseIsSent = new AtomicBoolean(false);

        System.out.println("---- DEBUT QMER ----");
        System.out.println(this.JIRA_LOGIN);
        System.out.println(this.JIRA_PASSWD);
        System.out.println(this.JIRA_LINK_URL);


        //{ "fields": {"project":{ "key": "NGMDP" },"summary": "REST test.","description": " REST API","issuetype": {"name": "Assistance" } }}

        StringBuilder ticket_jira = new StringBuilder()
                .append("{ \"fields\": {")
                .append("\"project\":{ \"key\": \"")
                .append(resource.getString(Supportpivot.COLLECTIVITY_FIELD))
                .append("\" },")
                .append("academie = ")
                .append(resource.getString(Supportpivot.ACADEMY_FIELD, ""))
                .append("demandeur = ")
                .append(resource.getString(Supportpivot.CREATOR_FIELD))
                .append("type_demande = ")
                .append(resource.getString(Supportpivot.TICKETTYPE_FIELD, ""))
                .append("titre = ")
                .append(resource.getString(Supportpivot.TITLE_FIELD))
                .append("description = ")
                .append(resource.getString(Supportpivot.TECHNICAL_RESP_FIELD, ""))
                .append("reponse_client = ")
                .append(resource.getString(Supportpivot.CLIENT_RESP_FIELD, ""))
                .append("attribution = ")
                .append(resource.getString(Supportpivot.ATTRIBUTION_FIELD));

        System.out.println(ticket_jira);


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
            // tester avec le web service en GET avec login et mdp
            // tester apres aves le POST
            // faire un json avec le JSON object et surtout pas en stringbuilder

        final HttpClientRequest httpClientRequest = httpClient.get(JIRA_LINK_URL  , new Handler<HttpClientResponse>() {
            @Override
            public void handle(HttpClientResponse response) {

                log.debug("On test le status :" + response.statusCode());
                int status = response.statusCode();

                if (response.statusCode() == 200) {
                    log.debug("Test OK status 200 !");
                } else {
                    log.error("Error when calling URL : " + response.statusMessage());
                    //renderError(request);
                }

            }
        });

        if (!responseIsSent.getAndSet(true)) {
            httpClient.close();
            log.debug("On close");
        }

        httpClientRequest.end();
            log.debug("On end");
        }


        System.out.println("---- FIN QMER ----");

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

    @Override
    public void sendToIWS(HttpServerRequest request, JsonObject resource, final Handler<Either<String, JsonObject>> handler) {

        StringBuilder mail = new StringBuilder()
            .append("collectivite = ")
            .append(resource.getString(Supportpivot.COLLECTIVITY_FIELD))
            .append("<br />academie = ")
            .append(resource.getString(Supportpivot.ACADEMY_FIELD, ""))
            .append("<br />demandeur = ")
            .append(resource.getString(Supportpivot.CREATOR_FIELD))
            .append("<br />type_demande = ")
            .append(resource.getString(Supportpivot.TICKETTYPE_FIELD, ""))
            .append("<br />titre = ")
            .append(resource.getString(Supportpivot.TITLE_FIELD))
            .append("<br />description = ")
            .append(resource.getString(Supportpivot.DESCRIPTION_FIELD))
            .append("<br />priorite = ")
            .append(resource.getString(Supportpivot.PRIORITY_FIELD, ""))
            .append("<br />id_jira = ")
            .append(resource.getString(Supportpivot.IDJIRA_FIELD, ""))
            .append("<br />id_ent = ")
            .append(resource.getString(Supportpivot.IDENT_FIELD))
            .append("<br />id_iws = ")
            .append(resource.getString(Supportpivot.IDIWS_FIELD, ""));
        //Boucle sur le champs commentaires du tableau JSON
        JsonArray comm =   resource.getArray(Supportpivot.COMM_FIELD, new JsonArray());
        for(int i=0 ; i<comm.size();i++){
            mail.append("<br />commentaires = ")
                    .append(comm.get(i));
        }
        //Boucle sur le champs modules du tableau JSON
        JsonArray modules =   resource.getArray(Supportpivot.MODULES_FIELD, new JsonArray());
        mail.append("<br />modules = ");
        for(int i=0 ; i<modules.size();i++){
            if(i > 0) {
                mail.append(", ");
            }
            mail.append(modules.get(i));
        }
        mail.append("<br />statut_iws = ")
            .append(resource.getString(Supportpivot.STATUSIWS_FIELD, ""))
            .append("<br />statut_ent = ")
            .append(resource.getString(Supportpivot.STATUSENT_FIELD, ""))
            .append("<br />statut_jira = ")
            .append(resource.getString(Supportpivot.STATUSJIRA_FIELD, ""))
            .append("<br />date_creation = ")
            .append(resource.getString(Supportpivot.DATE_CREA_FIELD, ""))
            .append("<br />date_resolution_iws = ")
            .append(resource.getString(Supportpivot.DATE_RESOIWS_FIELD, ""))
            .append("<br />date_resolution_ent = ")
            .append(resource.getString(Supportpivot.DATE_RESOENT_FIELD, ""))
            .append("<br />date_resolution_jira = ")
            .append(resource.getString(Supportpivot.DATE_RESOJIRA_FIELD, ""))
            .append("<br />reponse_technique = ")
            .append(resource.getString(Supportpivot.TECHNICAL_RESP_FIELD, ""))
            .append("<br />reponse_client = ")
            .append(resource.getString(Supportpivot.CLIENT_RESP_FIELD, ""))
            .append("<br />attribution = ")
            .append(resource.getString(Supportpivot.ATTRIBUTION_FIELD));

        // TODO Attach a file to the email

        System.out.println(mail);

        String mailTo = resource.getString("email");
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
                new Handler<Message<JsonObject>>(){
                    @Override
                    public void handle(Message<JsonObject> jsonObjectMessage) {
                        handler.handle(new Either.Right<String, JsonObject>(jsonObjectMessage.body()));
                    }
                });
    }

    @Override
    public void testMailToIWS(HttpServerRequest request, String mailTo, Handler<Either<String, JsonObject>> handler) {
        JsonObject resource = new JsonObject()
                .putString(Supportpivot.COLLECTIVITY_FIELD, "MDP")
                .putString(Supportpivot.ACADEMY_FIELD, "Paris")
                .putString(Supportpivot.CREATOR_FIELD, "Jean Dupont")
                .putString(Supportpivot.TICKETTYPE_FIELD, "Assistance")
                .putString(Supportpivot.TITLE_FIELD, "Demande g&eacute;n&eacute;rique de test")
                .putString(Supportpivot.DESCRIPTION_FIELD, "Demande afin de tester la cr&eacute;ation de demande vers IWS")
                .putString(Supportpivot.PRIORITY_FIELD, "Mineure")
                .putArray(Supportpivot.MODULES_FIELD,
                        new JsonArray().addString("Assistance ENT").addString("Administration"))
                .putString(Supportpivot.IDENT_FIELD, "42")
                .putArray(Supportpivot.COMM_FIELD, new JsonArray()
                    .addString("Jean Dupont| 17/11/2071 | La correction n'est pas urgente.")
                    .addString("Administrateur Etab | 10/01/2017 | La demande a &eacute;t&eacute; transmise"))
                .putString(Supportpivot.STATUSENT_FIELD, "Nouveau")
                .putString(Supportpivot.DATE_CREA_FIELD, "16/11/2017")
                .putString(Supportpivot.ATTRIBUTION_FIELD, "IWS")
                .putString("email", mailTo);

        sendToIWS(request, resource, handler);
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

}
