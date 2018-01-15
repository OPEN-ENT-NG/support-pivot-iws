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

    private final String MAIL_IWS;
    private final String COLLECTIVITY_DEFAULT;
    private final String ATTRIBUTION_DEFAULT;
    private final String TICKETTYPE_DEFAULT;
    private final String PRIORITY_DEFAULT;



    public DefaultDemandeServiceImpl(Vertx vertx, Container container, EmailSender emailSender) {
        this.mongo = MongoDb.getInstance();
        this.emailSender = emailSender;
        this.log = container.logger();
        this.MAIL_IWS = container.config().getString("mail-iws");
        this.COLLECTIVITY_DEFAULT = container.config().getString("default-collectivity");
        this.ATTRIBUTION_DEFAULT = container.config().getString("default-attribution");
        this.TICKETTYPE_DEFAULT = container.config().getString("default-tickettype");
        this.PRIORITY_DEFAULT = container.config().getString("default-priority");
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
        } else {
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

}
