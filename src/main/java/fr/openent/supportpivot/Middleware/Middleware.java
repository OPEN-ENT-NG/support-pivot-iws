package fr.openent.supportpivot.Middleware;

import io.vertx.core.http.HttpClientRequest;

public interface Middleware {
    HttpClientRequest handle(HttpClientRequest httpClientRequest) throws Error;
}
