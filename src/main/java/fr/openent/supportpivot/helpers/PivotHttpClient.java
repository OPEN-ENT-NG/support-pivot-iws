package fr.openent.supportpivot.helpers;

import fr.openent.supportpivot.Middleware.GlpiAuthMiddleware;
import fr.openent.supportpivot.Middleware.Middleware;
import fr.openent.supportpivot.constants.HttpConstants;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class PivotHttpClient {

    private HttpClient httpClient;
    private String basicAuthLogin = "";
    private String basicAuthPwd = "";
    private List<Middleware> middlewares = new ArrayList<>();

    private HashMap<String, String> middlewareProviders = new HashMap<String, String>() {{
        put("glpi.auth", GlpiAuthMiddleware.class.toString());
    }};


    private static final Logger log = LoggerFactory.getLogger(PivotHttpClient.class);


    public PivotHttpClient(HttpClient httpClient, List<String> middlewareNames) {
        middlewareNames.forEach(middlewareName -> {
            try {
                this.middlewares.add(this.instantiateMiddleware(middlewareName));
            } catch (Error e) {
                log.error("Middleware not found: " + e.getMessage());
            }
        });

        this.httpClient = httpClient;
    }

    public PivotHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @SuppressWarnings("unused")
    public PivotHttpClientRequest createGetRequest(String uri) {
        return createRequest(HttpConstants.METHOD_GET, uri, null);
    }

    @SuppressWarnings("unused")
    public PivotHttpClientRequest createPostRequest(String uri, String data) {
        return createRequest(HttpConstants.METHOD_POST, uri, data);
    }

    public PivotHttpClientRequest createRequest(String method, String uri, String data){
        return this.createRequest(method, uri, data, new ArrayList<>());
    }

    public PivotHttpClientRequest createRequest(String method, String uri, String data, List<String> middlewares) {

        List<Middleware> requestMiddlewares = new ArrayList<>(this.middlewares);

        middlewares.forEach(middlewareName -> {
            try {
                requestMiddlewares.add(this.instantiateMiddleware(middlewareName));
            } catch (Error e) {
                log.error("Middleware not found: " + e.getMessage());
            }
        });

        HttpClientRequest request;
        switch (method) {
            case HttpConstants.METHOD_POST:
                request = httpClient.post(uri);
                break;
            case HttpConstants.METHOD_GET:
                request = httpClient.get(uri);
                break;
            case HttpConstants.METHOD_PUT:
                request = httpClient.put(uri);
                break;
            case HttpConstants.METHOD_DELETE:
                request = httpClient.delete(uri);
                break;
            default:
                throw new IllegalArgumentException("unknown http method");
        }

        PivotHttpClientRequest pivotRequest =  new PivotHttpClientRequest(request, requestMiddlewares);
        if(data != null) {
            pivotRequest.setData(data);
        }

        if(!basicAuthLogin.isEmpty() && !basicAuthPwd.isEmpty()) {
            pivotRequest.setBasicAuth(basicAuthLogin, basicAuthPwd);
        }
        return pivotRequest;
    }

    private Middleware instantiateMiddleware(String middlewareName) throws Error {

        if(this.middlewareProviders.containsKey(middlewareName)) {
            try {
                Class middlewareClass = Class.forName(this.middlewareProviders.get(middlewareName));
                return (Middleware) middlewareClass.newInstance();
            } catch (ClassNotFoundException e) {
                throw new Error("Class not found: " + e.getMessage());
            } catch (IllegalAccessException | InstantiationException e) {
                throw new Error("Class can not be instantiate: " + e.getMessage());
            }
        }

        throw new Error("The given middleware is not set");
    }

}
