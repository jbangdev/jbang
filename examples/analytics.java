//usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS com.brsanthu:google-analytics-java:2.0.0
//DEPS info.picocli:picocli:4.1.4

import com.brsanthu.googleanalytics.GoogleAnalytics;
import com.brsanthu.googleanalytics.GoogleAnalyticsConfig;
import com.brsanthu.googleanalytics.request.DefaultRequest;
import picocli.CommandLine;

import java.text.MessageFormat;
import java.util.concurrent.Callable;

import static com.brsanthu.googleanalytics.internal.GaUtils.appendSystemProperty;
import static java.lang.System.*;

@CommandLine.Command(name = "analytics", mixinStandardHelpOptions = true, version = "0.1",
        description = "Send Google Analytics events")
public class analytics implements Callable<Integer> {

    @CommandLine.Option(names={"--id"}, description="Google Tracking ID", required = true)
    private String id = null;

    public static void main(String... args) {
        int exitCode = new CommandLine(new analytics()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {

        GoogleAnalytics ga;

        String agent = "{0}/{1} (Macintosh; U; Intel Mac OS X {2}; {3})";
        agent = MessageFormat.format(agent, "jbangx", "0.7.2", "42", "en");

        out.println("Setup GA with agent: " + agent);
        ga = GoogleAnalytics.builder()
                .withDefaultRequest(new DefaultRequest().documentHostName("jbang.dev"))
                .withTrackingId(id)
                .withConfig(new GoogleAnalyticsConfig().setUserAgent(agent))
                .build();

        out.println("Send Event");
        ga.event()
                .campaignSource("unknown")
                .documentPath("/app/event-build")
                .eventCategory("API")
                .eventAction("actioncd")
                .eventLabel("labelcd")
                .customDimension(1, System.getProperty("java.runtime.version") )
                .customDimension(2, System.getProperty("java.specification.vendor") )
                .customDimension(3, System.getProperty("java.vm.name"))
                .customDimension(4, System.getProperty("os.name"))
                .customDimension(5, System.getProperty("os.version"))
                .customDimension(6, System.getProperty("os.arch"))
                .send();
        out.println("Event sent");

       ga.pageView("/app/build", "build")
               .customDimension(1, System.getProperty("java.runtime.version") )
               .customDimension(2, System.getProperty("java.specification.vendor") )
               .customDimension(3, System.getProperty("java.vm.name"))
               .customDimension(4, System.getProperty("os.name"))
               .customDimension(5, System.getProperty("os.version"))
               .customDimension(6, System.getProperty("os.arch"))
               .send();
        out.println("pageView sent");

        return 0;
    }
}
