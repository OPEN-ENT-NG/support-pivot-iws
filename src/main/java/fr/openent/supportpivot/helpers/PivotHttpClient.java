package fr.openent.supportpivot.helpers;

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

    private static final Logger log = LoggerFactory.getLogger(PivotHttpClient.class);


    public PivotHttpClient(HttpClient httpClient, List<String> middlewareNames) {
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

        PivotHttpClientRequest pivotRequest =  new PivotHttpClientRequest(request);
        if(data != null) {
            pivotRequest.setData(data);
        }

        if(!basicAuthLogin.isEmpty() && !basicAuthPwd.isEmpty()) {
            pivotRequest.setBasicAuth(basicAuthLogin, basicAuthPwd);
        }
        return pivotRequest;
    }
}
