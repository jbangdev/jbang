//usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.2.0

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;

import static java.lang.System.*;

@Command(name = "envdetector", mixinStandardHelpOptions = true, version = "envdetector 0.1",
        description = "envdetector made with jbang")
class envdetector implements Callable<Integer> {

    public static void main(String... args) {
        int exitCode = new CommandLine(new envdetector()).execute(args);
        exit(exitCode);
    }

    @Override
    public Integer call() throws Exception { // your business logic goes here.

        Map<String, String> env = new TreeMap<>(String::compareTo);

        env.put("os.name", getProperty("os.name"));
        env.put("os.version", getProperty("os.version"));
        env.put("os.arch", getProperty("os.arch"));
        env.put("java.vendor", getProperty("java.vendor"));
        env.put("java.version", getProperty("java.version"));
        env.put("java.vm.name", getProperty("java.vm.name"));
        env.put("java.vm.version", getProperty("java.vm.version"));
        env.put("java.vm.vendor", getProperty("java.vm.vendor"));

        env.put("ci", getBuildSystemName());

        env.put("kubernetes", runningInKubernetes());

        String format = "%-20s = %s\n";
        env.entrySet().forEach(e -> out.printf(format, e.getKey(), e.getValue()));

        return 0;
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


