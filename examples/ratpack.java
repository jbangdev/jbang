//usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS org.slf4j:slf4j-simple:1.7.30,io.ratpack:ratpack-core:1.7.5

import ratpack.server.RatpackServer;
import ratpack.handling.Context;

public class ratpack {

  public static void main(String args[]) throws Exception {
    new ratpack().run();
  }

  void run() throws Exception {
     RatpackServer.start((server) -> {
      server.serverConfig(sc -> sc.port(8080));
      server.handlers((chain) -> {
        chain
          .get(this::renderWorld)
          .get(":name", this::renderName);
      });
    });
  }

  void renderName(Context ctx) {
    var name = ctx.getPathTokens().get("name");
    System.out.println(String.format("Hello %s", name));
    ctx.render(String.format("[renderName] Hello <%s>!\n", name));
  }

  void renderWorld(Context ctx) {
    var message = "Hello World!";
    System.out.println(message);
    ctx.render(String.format("[renderWorld] %s\n", message));
  }

}
