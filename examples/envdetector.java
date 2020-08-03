//usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.5.0
//DEPS com.brsanthu:google-analytics-java:2.0.0
//DEPS kr.motd.maven:os-maven-plugin:1.6.1
//DEPS org.slf4j:slf4j-nop:1.7.30

import com.brsanthu.googleanalytics.GoogleAnalytics;
import com.brsanthu.googleanalytics.GoogleAnalyticsConfig;
import com.brsanthu.googleanalytics.request.DefaultRequest;
import com.brsanthu.googleanalytics.request.EventHit;
import kr.motd.maven.os.Detector;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.Callable;

import static java.lang.Math.abs;
import static java.lang.Math.random;
import static java.lang.System.*;

@Command(name = "envdetector", mixinStandardHelpOptions = true, version = "envdetector 0.1",
        description = "envdetector made with jbang. It detects OS, Java and possible CI/Container environment and send it  as telemetry data to a Google Analytics account.")
class envdetector implements Callable<Integer> {

    @CommandLine.Option(names = {"--id"}, description = "Google Tracking ID, default is jbang author testing account. No data are traceable back to any individual. All anonymous.", defaultValue = "UA-156530423-1")
    String gid;

    @CommandLine.Option(names = {"--telemetry"}, description="Turn on/off telemetry sending (default:on)", defaultValue = "${JBANG_NO_TELEMETRY:-false}", negatable = true)
    boolean no_telemetry = false;

    public static void main(String... args) {
        int exitCode = new CommandLine(new envdetector()).execute(args);
        exit(exitCode);
    }

    @Override
    public Integer call() throws Exception { // your business logic goes here.

        Map<String, String> env = getDetectedMap();

        String format = "%-20s = %s\n";
        env.entrySet().forEach(e -> out.printf(format, e.getKey(), e.getValue()));

        String agent = new UserAgent("jbang-envdetect", "1.0").toString();

        GoogleAnalytics ga;

        ga = GoogleAnalytics.builder()
                .withDefaultRequest(new DefaultRequest().documentHostName("jbang.dev"))
                .withTrackingId(gid)
                .withConfig(new GoogleAnalyticsConfig()
                            .setUserAgent(agent)
                            .setEnabled(!no_telemetry)
                           .setThreadTimeoutSecs(2).setDiscoverRequestParameters(true))
                .withAppName("jbang-envdetect-app")
                .withAppVersion("jbang-envdetect-1.0")
                .build();

        EventHit x = ga.event()
                .campaignSource("unknown")
                .documentPath("/envdetector")
                .eventCategory("api")
                .eventAction("envdetected")
                .eventLabel("envdetector")
                .customDimension(1, env.get("java.vendor"))
                .customDimension(2, env.get("java.version"))
                .customDimension(3, env.get("java.vm.name"))
                .customDimension(4, env.get("java.vm.version"))
                .customDimension(5, env.get("java.vm.vendor"))
                .customDimension(6, env.get("linux.release"))
                .customDimension(7,env.get("linux.version"))
                .customDimension(8, env.get("os.arch"))
                .customDimension(9, env.get("os.name"))
                .customDimension(10, env.get("os.version"));

        if(no_telemetry) {
            System.out.println("\n\nTelemetry turned off. Sending nothing.");
        } else {
            System.out.println("\n\nSending the following to Google Analytics id: " + gid);
            out.println(x.toString());
            out.println(agent);

            x.send();

        }

        return 0;
    }

    private Map<String, String> getDetectedMap() {

        new GADetector().detect(new Properties(), Collections.emptyList());

        Map<String, String> env = new TreeMap<>(String::compareTo);

        env.put("os.name", getProperty("os.name"));
        env.put("os.version", getProperty("os.version"));
        env.put("os.arch", getProperty("os.arch"));
        env.put("java.vendor", getProperty("java.vendor"));
        env.put("java.version", getProperty("java.version"));
        env.put("java.vm.name", getProperty("java.vm.name"));
        env.put("java.vm.version", getProperty("java.vm.version"));
        env.put("java.vm.vendor", getProperty("java.vm.vendor"));
        env.put("linux.release", getProperty("os.detected.release", "N/A"));
        env.put("linux.version", getProperty("os.detected.release.version", "N/A"));

        env.put("detected.ci", getBuildSystemName());

        env.put("detected.kubernetes", runningInKubernetes());
        return env;
    }

    private String runningInKubernetes() {
        return Boolean.toString(allEnvSet(
                "KUBERNETES_SERVICE_HOST",
                "KUBERNETES_SERVICE_PORT",
                "KUBERNETES_SERVICE_HOST"));
    }

    boolean allEnvSet(String... names) {
        for(String name : names) {
            if(getenv(name)==null) {
                return false;
            }
        }
        return true;
    }

    private String getBuildSystemName() {

        String travis = getenv("TRAVIS");
        String user = getenv("USER");
        if("true".equals(travis) && "travis".equals(user)) {
            return "travis";
        }

        if(allEnvSet("JENKINS_URL", "JENKINS_HOME", "WORKSPACE")) {
            return "jenkins";
        }

        if(allEnvSet("GITHUB_WORKFLOW", "GITHUB_WORKSPACE")) {
            return "github-actions";
        }

        // https://docs.microsoft.com/en-us/azure/devops/pipelines/build/variables?view=azure-devops&tabs=yaml
        if(allEnvSet("BUILD_REASON", "AGENT_JOBSTATUS")) {
            return "azure-pipelines";
        }

        return "unknown";
    }


}

class GADetector extends Detector {

    @Override
    protected void log(String s) {
    }

    @Override
    protected void logProperty(String s, String s1) {
    }

    public void detect(Properties properties, List<String> classiferWithLikes) {
        super.detect(properties, classiferWithLikes);
    }
};

class UserAgent {

    public static final char BROWSER_LOCALE_DELIMITER = '-';

    public static final char JAVA_LOCALE_DELIMITER = '_';
    
    private static final String USERAGENT_WIN = "{0}/{1} (Windows; U; Windows NT {2}; {3})";
    private static final String USERAGENT_WIN_64 = "{0}/{1} (Windows; U; Windows NT {2}; Win64; x64; {3})";
    private static final String USERAGENT_MAC = "{0}/{1} (Macintosh; U; Intel Mac OS X {2}; {3})";
    private static final String USERAGENT_LINUX = "{0}/{1} (X11; U; Linux i686; {3})";
    private static final String USERAGENT_LINUX_64 = "{0}/{1} (X11; U; Linux x86_64; {3})";

    private static final String ARCHITECTURE_64 = "64";
    private final String appname;
    private final String appversion;

    private String browserLanguage;

    UserAgent(String appname, String appversion) {
        this.appname = appname;
        this.appversion = appversion;
    }
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
        return System.getProperty("os.detected.name");
    }

    public String getJavaArchitecture() {
        return System.getProperty("os.detected.arch");
    }

    public String getOSVersion() {
        return System.getProperty("os.version");
    }

    private String getUserAgentPattern() {
        String os = getOS();
        if ("windows".equals(os)) {
            if (is64()) {
                return USERAGENT_WIN_64;
            } else {
                return USERAGENT_WIN;
            }
        } else if ("osx".equals(os)) {
            return USERAGENT_MAC;
        } else  { // assume its linux based
            if (is64()) {
                return USERAGENT_LINUX;
            } else {
                return USERAGENT_LINUX_64;
            }
        }
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
        return appname;
    }

    public String getApplicationVersion() {
        return appversion;
    }


}
