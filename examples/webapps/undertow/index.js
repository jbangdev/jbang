/**
 * Author: Bruno Borges (@brunoborges)
 * Since: 2015
 */
var Undertow = Packages.io.undertow.Undertow;
var Headers = Packages.io.undertow.util.Headers;
var server = Undertow.builder()
                .addHttpListener(8080, "localhost")
                .setHandler(function(exchange) {
                        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
                        exchange.getResponseSender().send("Hello World");
                }).build();
        server.start();

Nasven.daemon();
