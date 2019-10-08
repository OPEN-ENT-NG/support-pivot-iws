package fr.openent.supportpivot.model.endpoint;

import fr.openent.supportpivot.deprecatedservices.DemandeService;
import fr.openent.supportpivot.managers.ConfigManager;
import fr.openent.supportpivot.services.HttpClientService;
import io.vertx.core.Vertx;

public class EndpointFactory {

    private HttpClientService httpClientService;
    private Vertx vertx;

    public EndpointFactory(HttpClientService httpClientService, DemandeService demandeService, Vertx vertx) {
        this.httpClientService = httpClientService;
        this.vertx = vertx;

    }

    public Endpoint getGlpiEndpoint()  {
        return new GlpiEndpoint(this.httpClientService);
    }

    /*public Endpoint getJiraEndpoint()  {
        return new JiraEndpoint(this.httpClientService);
    }*/

    public Endpoint getPivotEndpoint()  {
        return new PivotEndpoint(this.vertx);
    }
}
