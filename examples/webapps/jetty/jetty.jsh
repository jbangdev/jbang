//usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS org.eclipse.jetty:jetty-server:9.4.25.v20191220

import org.eclipse.jetty.server.Server;

var server = new Server(8080);
server.start();
server.dumpStdErr();
server.join();

/exit
