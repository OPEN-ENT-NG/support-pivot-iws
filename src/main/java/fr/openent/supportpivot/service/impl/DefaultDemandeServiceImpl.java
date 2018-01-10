package fr.openent.supportpivot.service.impl;

import fr.openent.supportpivot.Supportpivot;
import fr.openent.supportpivot.service.DemandeService;
import fr.wseduc.mongodb.MongoDb;
import fr.wseduc.webutils.Either;
import org.entcore.common.email.EmailFactory;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import fr.wseduc.webutils.email.EmailSender;
import org.vertx.java.platform.Container;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by colenot on 07/12/2017.
 */
public class DefaultDemandeServiceImpl implements DemandeService {

    private static final String DEMANDE_COLLECTION = "support.demandes";
    private final MongoDb mongo;
    private final EmailSender emailSender;

    public DefaultDemandeServiceImpl(Vertx vertx, Container container, EmailSender emailSender) {
        this.mongo = MongoDb.getInstance();
        this.emailSender = emailSender;
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
            add(request, resource, handler);
        }

    }


    @Override
    public void add(HttpServerRequest request, JsonObject resource, final Handler<Either<String, JsonObject>> handler) {

        // Insert data into mongodb
        mongo.insert(DEMANDE_COLLECTION, resource, new Handler<Message<JsonObject>>() {
            @Override
            public void handle(Message<JsonObject> retourJson) {
                JsonObject body = retourJson.body();
                if("ok".equals(body.getString("status"))) {
                    handler.handle(new Either.Right<String, JsonObject>(new JsonObject().putString("status","OK")));
                }
                else {
                    handler.handle(new Either.Left<String, JsonObject>("1"));
                }
            }
        });

    }


    @Override
    public void sendToIWS(HttpServerRequest request, JsonObject resource, final Handler<Either<String, JsonObject>> handler) {

        StringBuilder mail = new StringBuilder()
            .append("collectivite = " + resource.getString(Supportpivot.COLLECTIVITY_FIELD))
            .append("<br />" + "academie = " + resource.getString(Supportpivot.ACADEMY_FIELD, ""))
            .append("<br />" + "demandeur = " + resource.getString(Supportpivot.CREATOR_FIELD))
            .append("<br />" + "type_demande = " + resource.getString(Supportpivot.TICKETTYPE_FIELD, ""))
            .append("<br />" + "titre = " + resource.getString(Supportpivot.TITLE_FIELD))
            .append("<br />" + "description = " + resource.getString(Supportpivot.DESCRIPTION_FIELD))
            .append("<br />" + "priorite = " + resource.getString(Supportpivot.PRIORITY_FIELD, ""))
            .append("<br />" + "modules = " + resource.getString(Supportpivot.MODULES_FIELD, ""))
            .append("<br />" + "id_jira = " + resource.getString(Supportpivot.IDJIRA_FIELD, ""))
            .append("<br />" + "id_ent = " + resource.getString(Supportpivot.IDENT_FIELD))
            .append("<br />" + "id_iws = " + resource.getString(Supportpivot.IDIWS_FIELD, ""));
            //Boucle sur le champs commentaires du tableau JSON
            JsonArray Comm =   resource.getArray(Supportpivot.COMM_FIELD, new JsonArray());
            for(int i=0 ; i<Comm.size();i++){
                mail.append("<br />" + "commentaires = " + Comm.get(i));
            }
            mail.append("<br />" + "statut_iws = " + resource.getString(Supportpivot.STATUSIWS_FIELD, ""))
            .append("<br />" + "statut_ent = " + resource.getString(Supportpivot.STATUSENT_FIELD, ""))
            .append("<br />" + "statut_jira = " + resource.getString(Supportpivot.STATUSJIRA_FIELD, ""))
            .append("<br />" + "date_creation = " + resource.getString(Supportpivot.DATE_CREA_FIELD, ""))
            .append("<br />" + "date_resolution_iws = " + resource.getString(Supportpivot.DATE_RESOIWS_FIELD, ""))
            .append("<br />" + "date_resolution_ent = " + resource.getString(Supportpivot.DATE_RESOENT_FIELD, ""))
            .append("<br />" + "date_resolution_jira = " + resource.getString(Supportpivot.DATE_RESOJIRA_FIELD, ""))
            .append("<br />" + "reponse_technique = " + resource.getString(Supportpivot.TECHNICAL_RESP_FIELD, ""))
            .append("<br />" + "reponse_client = " + resource.getString(Supportpivot.CLIENT_RESP_FIELD, ""))
            .append("<br />" + "attribution = " + resource.getString(Supportpivot.ATTRIBUTION_FIELD));

        // TODO Attach a file to the email

        System.out.println(mail);

        //TODO RECUPERER L ADRESSE DANS LA CONF
        emailSender.sendEmail(request,
                "admin@example.com",
                null,
                null,
                "TICKETCGI",
                mail.toString(),
                null,
                false,
                new Handler<Message<JsonObject>>(){
                    @Override
                    public void handle(Message<JsonObject> jsonObjectMessage) {

                    }
                });
    }

}
