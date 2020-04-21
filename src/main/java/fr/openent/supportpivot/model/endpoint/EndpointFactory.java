package fr.openent.supportpivot.model.endpoint;

import fr.openent.supportpivot.model.endpoint.jira.JiraEndpoint;
import fr.openent.supportpivot.services.GlpiService;
import fr.openent.supportpivot.services.HttpClientService;
import fr.openent.supportpivot.services.JiraService;
import io.vertx.core.Vertx;

public  class EndpointFactory {



    public static Endpoint getGlpiEndpoint(GlpiService glpiService) {
        return new GlpiEndpoint(glpiService);
    }

    public static JiraEndpoint getJiraEndpoint(HttpClientService httpClientService, JiraService jiraService) {
        return new JiraEndpoint(httpClientService, jiraService);
    }

    public static Endpoint getPivotEndpoint(Vertx vertx) {
        return new PivotEndpoint(vertx);
    }

    public static LdeEndPoint getLdeEndpoint() {
        return new LdeEndPoint();
    }
}
