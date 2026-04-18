package info.tongrenlu.service;

import cn.hutool.http.HttpResponse;

/**
 * Functional interface for HTTP execution in ThbwikiService.
 * Enables test mocking of external HTTP calls without network I/O.
 */
@FunctionalInterface
public interface ThbwikiHttpClient {

    /**
     * Execute an HTTP GET request and return the response.
     *
     * @param url the URL to request
     * @return the HTTP response object (caller is responsible for closing)
     */
    HttpResponse execute(String url);
}
