package fr.openent.supportpivot.helpers;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class PivotHttpClientRequest {

    private HttpClientRequest httpClientRequest;
    private String basicAuth = "";
    private String data = "";

    private static Base64.Encoder encoder = Base64.getMimeEncoder().withoutPadding();

    PivotHttpClientRequest(HttpClientRequest httpClientRequest) {
        this.httpClientRequest = httpClientRequest;
    }

    @SuppressWarnings("WeakerAccess")
    public void setBasicAuth(String login, String passwd) {
        String basicAuthStr = login + ":" + passwd;
        basicAuth = encoder.encodeToString(basicAuthStr.getBytes());
    }

    @SuppressWarnings("unused")
    public void startRequest(Handler<AsyncResult<HttpClientResponse>> handler) {

        httpClientRequest.handler(response -> handler.handle(Future.succeededFuture(response)));
        httpClientRequest.exceptionHandler(response -> handler.handle(Future.failedFuture(response.toString())));
        terminateRequest(httpClientRequest);

    }

    private void terminateRequest(HttpClientRequest httpClientRequest) {
        if (!data.isEmpty()) {
            httpClientRequest.setChunked(true).write(data);
        }
        if (!basicAuth.isEmpty()) {
            httpClientRequest.putHeader("Authorization", "Basic " + basicAuth);
        }
        httpClientRequest.setFollowRedirects(true);
        if (!httpClientRequest.headers().contains("Content-Type")) {
            httpClientRequest.putHeader("Content-Type", "application/json");
        }
        httpClientRequest.end();
    }

    void setData(String data) {
        this.data = data;
    }

    public HttpClientRequest getHttpClientRequest() {
        return httpClientRequest;
    }
}
