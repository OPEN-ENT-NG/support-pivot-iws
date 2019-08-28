package fr.openent.supportpivot.helpers;

import fr.openent.supportpivot.constants.HttpConstants;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;



public class PivotHttpClient {

    private HttpClient httpClient;
    private String basicAuthLogin = "";
    private String basicAuthPwd = "";

    public PivotHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @SuppressWarnings("unused")
    public void setBasicAuth(String login, String passwd) {
        this.basicAuthLogin = login==null ? "" : login;
        this.basicAuthPwd = passwd==null ? "" : passwd;
    }

    @SuppressWarnings("unused")
    public PivotHttpClientRequest createGetRequest(String uri) {
        return createRequest(HttpConstants.METHOD_GET, uri, null);
    }

    @SuppressWarnings("unused")
    public PivotHttpClientRequest createPostRequest(String uri, String data) {
        return createRequest(HttpConstants.METHOD_POST, uri, data);
    }

    private PivotHttpClientRequest createRequest(String method, String uri, String data) {
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
