package fr.openent.supportpivot.helpers;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.Base64;

public class PivotHttpClientRequest {

    private HttpClientRequest httpClientRequest;
    private String basicAuth = "";
    private String data = "";

    private static Base64.Encoder encoder = Base64.getMimeEncoder().withoutPadding();

    private static final Logger log = LoggerFactory.getLogger(PivotHttpClientRequest.class);

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


        httpClientRequest.handler(response ->
                {

                    response.exceptionHandler(exception ->
                            {
                                log.error("Error when execute http query" , exception);
                                handler.handle(Future.failedFuture(exception));
                            }
                    );
                    handler.handle(Future.succeededFuture(response));
                });

        httpClientRequest.exceptionHandler(response -> handler.handle(Future.failedFuture(response)));
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
