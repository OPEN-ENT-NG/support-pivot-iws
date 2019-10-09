package fr.openent.supportpivot.services;

import fr.openent.supportpivot.constants.HttpConstants;
import fr.openent.supportpivot.helpers.PivotHttpClient;
import fr.openent.supportpivot.managers.ConfigManager;
import fr.openent.supportpivot.services.routers.CrnaRouterService;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.Vertx;
import io.vertx.core.net.ProxyOptions;

import java.net.URI;
import java.net.URISyntaxException;

public class HttpClientService {

    private Vertx vertx;

    public HttpClientService(Vertx vertx) {
        this.vertx = vertx;
    }

    @SuppressWarnings("unused")
    public PivotHttpClient getHttpClient(String hostUri) throws URISyntaxException {
        URI host = new URI(hostUri);
        HttpClientOptions httpClientOptions = getOptions(host);
        HttpClient httpClient = vertx.createHttpClient(httpClientOptions);
        return new PivotHttpClient(httpClient);
    }

    private HttpClientOptions getOptions(URI uri) {
        final HttpClientOptions options = new HttpClientOptions();
        if (ConfigManager.getInstance().getProxyHost() != null) {
            ProxyOptions proxy = new ProxyOptions();
            proxy.setHost(ConfigManager.getInstance().getProxyHost()); //"fr-proxy.groupinfra.com"
            proxy.setPort(ConfigManager.getInstance().getProxyPort()); //3128
            options.setProxyOptions(proxy);
        }

        return options
                .setDefaultHost(uri.getHost())
                .setDefaultPort((uri.getPort() > 0) ? uri.getPort() :
                        (HttpConstants.HTTPS.equals(uri.getScheme()) ? 443 : 80))
                .setVerifyHost(false)
                .setTrustAll(true)
                .setSsl(HttpConstants.HTTPS.equals(uri.getScheme()))
                .setKeepAlive(true);
    }


}
