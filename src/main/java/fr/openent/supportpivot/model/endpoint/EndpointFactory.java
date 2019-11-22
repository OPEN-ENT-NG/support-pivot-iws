package fr.openent.supportpivot.model.endpoint;

import fr.openent.supportpivot.deprecatedservices.DefaultDemandeServiceImpl;
import fr.openent.supportpivot.deprecatedservices.DefaultJiraServiceImpl;
import fr.openent.supportpivot.services.GlpiService;
import fr.openent.supportpivot.services.HttpClientService;
import io.vertx.core.Vertx;

public class EndpointFactory {

    private HttpClientService httpClientService;
    private Vertx vertx;
    private DefaultDemandeServiceImpl demandeService;
    private DefaultJiraServiceImpl jiraService;
    private GlpiService glpiService;

    public EndpointFactory(HttpClientService httpClientService, DefaultDemandeServiceImpl demandeService, DefaultJiraServiceImpl jiraService, GlpiService glpiService, Vertx vertx) {
        this.httpClientService = httpClientService;
        this.vertx = vertx;
        this.demandeService = demandeService;
        this.jiraService = jiraService;
        this.glpiService = glpiService;
    }

    public Endpoint getGlpiEndpoint() {
        return new GlpiEndpoint(this.glpiService);
    }

    public Endpoint getJiraEndpoint() {
        return new JiraEndpoint(this.httpClientService, demandeService, jiraService);
    }

    public Endpoint getPivotEndpoint() {
        return new PivotEndpoint(this.vertx);
    }
}
