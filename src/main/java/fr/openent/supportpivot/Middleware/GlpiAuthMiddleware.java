package fr.openent.supportpivot.Middleware;

import fr.openent.supportpivot.helpers.LoginTool;
import io.vertx.core.http.HttpClientRequest;

public class GlpiAuthMiddleware implements Middleware{
    private String token;

    @Override
    public HttpClientRequest handle(HttpClientRequest httpClientRequest) throws Error {
        if (this.token == null || this.token.isEmpty()) {
            /*LoginTool.getGlpiSessionToken(config, this.httpClient, handler -> {
                if (handler.succeeded()) {
                    this.token = handler.result();
                } else {
                    log.error(handler.cause().getMessage(), (Object) handler.cause().getStackTrace());
                }
            });*/
        } else {

        }


        return httpClientRequest;
    }
}
