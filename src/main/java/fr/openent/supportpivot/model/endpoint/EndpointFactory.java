package fr.openent.supportpivot.model.endpoint;

public class EndpointFactory {

    public static Endpoint getGlpiEndpoint()  {
        return new GlpiEndpoint();
    }
}
