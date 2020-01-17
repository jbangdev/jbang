//usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS io.undertow:undertow-core:2.0.29.Final

import io.undertow.Undertow;
import io.undertow.util.Headers;

class undertow {

  public static void main(String args[]) {
    var server = Undertow.builder().addHttpListener(8080, "localhost").setHandler((exchange) -> {
      exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
      exchange.getResponseSender().send("Hello World");
    }).build();

    server.start();
  }

}
