//usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS com.brsanthu:google-analytics-java:2.0.0
//DEPS info.picocli:picocli:4.5.0

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

    @CommandLine.Option(names = {"--id"}, description = "Google Tracking ID", required = true)
    private String id = null;

    public static void main(String... args) {
        int exitCode = new CommandLine(new analytics()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {

        new GADetector().detect(new Properties(), Collections.emptyList());

        System.getProperties().keySet().stream().sorted().forEach(e -> System.out.println(e + "=" + System.getProperty(e.toString())));

        GoogleAnalytics ga;

        //String agent = "{0}/{1} (Macintosh; U; Intel Mac OS X {2}; {3})";
        //agent = MessageFormat.format(agent, "jbangx", "0.7.2", "42", "en");

        String agent = new UserAgent().toString();

        out.println("Setup GA with agent: " + agent);
        ga = GoogleAnalytics.builder()
                .withDefaultRequest(new DefaultRequest().documentHostName("jbang.dev"))
                .withTrackingId(id)
                .withConfig(new GoogleAnalyticsConfig().setUserAgent(agent))
                .build();

        out.println("Send Event");
        ga.screenView().sessionControl("start").send();

        ga.pageView()
                .documentTitle("/build")
                .send();

        ga.exception().exceptionDescription("something failed").send();


        EventHit x = ga.event()
                .campaignSource("unknown")
                .documentPath("/app/event-build")
                .eventCategory("API")
                .eventAction("actioncd")
                .eventLabel("labelcd")
                .customDimension(1, getProperty("java.vendor"))
                .customDimension(2, getProperty("java.version"))
                .customDimension(3, getProperty("java.vm.name"))
                .customDimension(4, getProperty("java.vm.version"))
                .customDimension(5, getProperty("java.vm.vendor"));

        out.println(x.toString());

        x.send();
        out.println("Event sent");

        ga.screenView().sessionControl("end").send();

        return 0;
    }
}

class UserAgent {

    public static final char BROWSER_LOCALE_DELIMITER = '-';

    public static final char JAVA_LOCALE_DELIMITER = '_';

    //private static final String ECLIPSE_RUNTIME_BULDEID = "org.eclipse.core.runtime"; //$NON-NLS-1$

    private static final String USERAGENT_WIN = "{0}/{1} (Windows; U; Windows NT {2}; {3})"; //$NON-NLS-1$
    private static final String USERAGENT_WIN_64 = "{0}/{1} (Windows; U; Windows NT {2}; Win64; x64; {3})"; //$NON-NLS-1$
    private static final String USERAGENT_MAC = "{0}/{1} (Macintosh; U; Intel Mac OS X {2}; {3})"; //$NON-NLS-1$
    private static final String USERAGENT_LINUX = "{0}/{1} (X11; U; Linux i686; {3})"; //$NON-NLS-1$
    private static final String USERAGENT_LINUX_64 = "{0}/{1} (X11; U; Linux x86_64; {3})"; //$NON-NLS-1$

    public static final char VERSION_DELIMITER = '.'; //$NON-NLS-1$

    private static final String PROP_OS_VERSION = "os.version"; //$NON-NLS-1$
    private static final String PROP_SUN_ARCH = "sun.arch.data.model"; //$NON-NLS-1$

    private static final String ARCHITECTURE_64 = "64";

    private String browserLanguage;

    public String toString() {
        String productId = getApplicationName();
        String productVersion = getApplicationVersion();

        return MessageFormat.format(
                getUserAgentPattern()
                , productId
                , productVersion
                , getOSVersion()
                , getBrowserLanguage()
        );
    }

    private String createBrowserLanguage() {
        return Locale.getDefault().toString().replace(JAVA_LOCALE_DELIMITER,BROWSER_LOCALE_DELIMITER);
    }

    public String getBrowserLanguage() {
        if (browserLanguage == null) {
            browserLanguage = createBrowserLanguage();
        }
        return browserLanguage;
    }

    public String getOS() {
        return System.getProperty("os.detected");
    }

    public String getJavaArchitecture() {
        return System.getProperty("os.detected.arch");
    }

    public String getOSVersion() {
        return System.getProperty("os.version");
    }

    private String getUserAgentPattern() {
        String os = getOS();
        String userAgentPattern = ""; //$NON-NLS-1$
        /*if (Platform.OS_LINUX.equals(os)) {
            if (is64()) {
                return USERAGENT_LINUX_64;
            } else {
                return USERAGENT_LINUX;
            }
        } else if (Platform.OS_MACOSX.equals(os)) {
            return USERAGENT_MAC;
        } else if (Platform.OS_WIN32.equals(os)) {
            if (is64()) {
                return USERAGENT_WIN_64;
            } else {
                return USERAGENT_WIN;
            }
        }*/
        userAgentPattern = USERAGENT_WIN_64;
        return userAgentPattern;
    }

    /**
     * Returns <code>true</code> if the jvm this is running in is a 64bit jvm.
     *
     * @return
     *
     * @see <a href="// http://stackoverflow.com/questions/807263/how-do-i-detect-which-kind-of-jre-is-installed-32bit-vs-64bit">stackoverflow</a>
     */
    private boolean is64() {
        String architecture = getJavaArchitecture();
        return architecture != null
                && architecture.equals(ARCHITECTURE_64);
    }

    public String getApplicationName() {
        return "jbang";
    }

    public String getApplicationVersion() {
        return "1." + (int)abs(random()*42);
    }


}

