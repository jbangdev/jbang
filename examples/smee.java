//usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.5.0
//DEPS org.jboss.resteasy:resteasy-client:4.4.1.Final
//DEPS com.fasterxml.jackson.core:jackson-databind:2.2.3

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.HttpRequest;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.engines.ApacheHttpClient43Engine;
import org.jboss.resteasy.spi.ResteasyConfiguration;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import javax.ws.rs.HttpMethod;
import javax.ws.rs.client.*;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.sse.InboundSseEvent;
import javax.ws.rs.sse.SseEventSource;
import java.io.IOException;
import java.net.*;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static picocli.CommandLine.*;

/**
 * A pure java implementation of smee-client.
 */
@Command(name = "smee", mixinStandardHelpOptions = true, version = "smee 0.1",
        description = "smee made with jbang", showDefaultValues = true)
class smee implements Callable<Integer> {

    private static final Logger log;

    static {
        System.setProperty("java.util.logging.SimpleFormatter.format",
                "%5$s %n");
        log = Logger.getLogger(smee.class.getName());
    }
    @Option(names = {"-u","--url"}, description = "URL of the webhook proxy service\n  Default: Fetch new from https://smee.io/new")
    private String url;

    @Option(names={"-t", "--target"},
            description = "Full URL (including protocol and path) of the target service the events will forwarded to\n  Default: http://127.0.0.1:PORT/PATH")
    private String target;

    @Option(names={"-p","--port"}, defaultValue = "${PORT:-3000}", description = "Local HTTP server port")
    int port;

    @Option(names={"-P", "--path"}, defaultValue = "/", description = "URL path to post proxied requests to")
    String path;

    public static void main(String... args) {
        int exitCode = new CommandLine(new smee()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception { // your business logic goes here...

        if(target==null) {
            target = String.format("http://127.0.0.1:%s%s", port, path);
        }

        if(url==null) {
            url = createChannel();
        }

        URI uri = new URI(url);

        Client client = ClientBuilder.newBuilder()
                                     .build();
                                     //.register(HTTPLoggingFilter.class);

        final var events = SseEventSource.target(client.target(uri))
                                          // Reconnect immediately
                                         .reconnectingEvery(0, TimeUnit.MILLISECONDS).build();

        events.register(this::onMessage, this::onError);

        log.info("Forwarding " + url + " to " + target);
        events.open();

        if(events.isOpen()) {
            log.info("Connected " + url);
        }

        Thread.currentThread().join();

        return 0;
    }

    private void onError(Throwable error) {
        log.severe(error.getMessage());
    }

    private void onMessage(InboundSseEvent event) {
        if("ping".equals(event.getName()) || "ready".equals(event.getName())) {
            return;
        }
        ObjectMapper mapper = new ObjectMapper();
        try {
            ObjectNode data = (ObjectNode) mapper.readTree(event.readData());

            var urib = UriBuilder.fromUri(this.target);

            if(data.has("query")) {
                urib.replaceQuery(URLEncoder.encode(data.get("query").asText(), "UTF-8"));
                data.remove("query");
            }

            try(CloseableHttpClient client = HttpClientBuilder.create().disableCookieManagement().build()) {

                var request = new HttpPost(urib.build());

                if(data.has("body")) {
                    request.setEntity(new StringEntity(data.get("body").asText()));
                    data.remove("body");
                }

                data.fieldNames().forEachRemaining(s ->
                {
                    if(!s.equalsIgnoreCase("content-length")) {
                    request.setHeader(s, data.get(s).asText());
                }});


                CloseableHttpResponse response = client.execute(request);

                if(response.getStatusLine().getStatusCode()!=200) {
                    log.severe(response.getStatusLine().toString());
                } else {
                    log.info(request.getMethod() + " " + request.getURI() + " - " + response.getStatusLine().getStatusCode());
                }
            }

        } catch (IOException e) {
            log.warning("Could not parse event data: " + e.getMessage());
            e.printStackTrace();
        } catch (RuntimeException re) {
            log.warning("Could not parse event data: " + re.getMessage());
            re.printStackTrace();
        }

    }

    private String createChannel() throws IOException {
        HttpURLConnection con = (HttpURLConnection)(new URL( "https://smee.io/new" ).openConnection());
        con.setInstanceFollowRedirects( false );
        con.connect();
        return con.getHeaderField( "Location" );
    }

    static public class HTTPLoggingFilter implements ClientRequestFilter, ClientResponseFilter {

        private static final Logger logger = Logger.getLogger(HTTPLoggingFilter.class.getName());

        @Override
        public void filter(ClientRequestContext requestContext) throws IOException {
            logger.info("request:" + requestContext.getUri().toString());
            logger.info("request:" + requestContext.getHeaders().toString());

        }

        @Override
        public void filter(ClientRequestContext requestContext, ClientResponseContext responseContext) throws IOException {
            //
            // logger.info(responseContext.getUri().toString());
            logger.info("response: " + responseContext.getHeaders().toString());
        }
    }
}
