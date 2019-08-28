package fr.openent.supportpivot.model.endpoint;

import fr.openent.supportpivot.deprecatedservices.DemandeService;
import fr.openent.supportpivot.managers.ConfigManager;
import fr.openent.supportpivot.services.HttpClientService;

public class EndpointFactory {

    private HttpClientService httpClientService;
    private DemandeService demandeService;
    private ConfigManager config;

    public EndpointFactory(ConfigManager config, HttpClientService httpClientService, DemandeService demandeService) {
        this.config = config;
        this.httpClientService = httpClientService;
        this.demandeService = demandeService;

    }

    public Endpoint getGlpiEndpoint()  {
        return new GlpiEndpoint(this.config, this.httpClientService);
    }

    /*public Endpoint getJiraEndpoint()  {
        return new JiraEndpoint(this.httpClientService);
    }*/

    public Endpoint getPivotEndpoint()  {
        return new PivotEndpoint(this.httpClientService, this.demandeService);
    }
}
