///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17+

//DEPS org.kohsuke:github-api:1.323
//DEPS info.picocli:picocli:4.7.6


import java.io.IOException;

import org.kohsuke.github.GHFileNotFoundException;
import org.kohsuke.github.GHOrganization;
import org.kohsuke.github.GHRef;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.GitHubBuilder;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "releasecheck", mixinStandardHelpOptions = true, version = "releasecheck 1.0",
        description = "Checks the release status of various repositories.")
public class releasecheck implements Runnable {

    @Parameters(index = "0", description = "The version to check, i.e. '0.117.0'.")
    private String version;

    private GitHub github;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new releasecheck()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        try {
            github = new GitHubBuilder().build();
            System.out.print("jbangdev/jbang-action...");
            if(checkJbangActionRelease(version)) {
                System.out.printf("%s available\n".formatted(version));
            } else {
                System.out.printf("is missing. Check https://github.com/jbangdev/jbang-action/releases\n".formatted(version));
            }
            System.out.print("jbangdev/scoop-bucket...");
            if(checkScoopBucketTag(version)) {
                System.out.printf("%s tag available\n".formatted(version));
            } else {
                System.out.printf("%s tag missing\n".formatted(version));
            }
            System.out.print("Chocolatey...");
            if(checkChocolateyPackage(version)) {
                System.out.printf("%s package available\n".formatted(version));
            } else {
                System.out.printf("%s package missing\n".formatted(version));
            }
            System.out.print("SDKMan...");
            if(checkSDKMan(version)) {
                System.out.printf("%s release available\n".formatted(version));
            } else {
                System.out.printf("%s release missing\n".formatted(version));
            }
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    private boolean checkJbangActionRelease(String version) throws IOException {
        String tag = "v" + version;
        GHOrganization organization = github.getOrganization("jbangdev");
        GHRepository repository = organization.getRepository("jbang-action");
        var actionRelease = repository.getReleaseByTagName(tag);

        return actionRelease != null;
    }

    private boolean checkScoopBucketTag(String version) throws IOException {
        String tag = "v" + version;
        GHOrganization organization = github.getOrganization("jbangdev");
        GHRepository scoopBucketRepository = organization.getRepository("scoop-bucket");
        GHRef scoopBucketTag = null;

        try {
            scoopBucketTag = scoopBucketRepository.getRef("tags/" + tag);
        } catch (GHFileNotFoundException e) {
            return false;
        }

        return scoopBucketTag != null;
    }

    private boolean checkChocolateyPackage(String version) {
        try {
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new java.net.URL("https://community.chocolatey.org/packages/jbang/" + version).openConnection();
            connection.setRequestMethod("GET");
            int responseCode = connection.getResponseCode();
            if (responseCode == 404) {
                return false;
            } else {
                return true;
            }
        } catch (IOException e) {
            System.err.println("Error checking Chocolatey: " + e.getMessage());
            return false;
        }
    }

    private boolean checkSDKMan(String version) {
        try {
            java.net.HttpURLConnection connection = (java.net.HttpURLConnection) new java.net.URL("https://api.sdkman.io/2/candidates/jbang/64/versions/list?current=" + version).openConnection();
            connection.setRequestMethod("GET");
            int responseCode = connection.getResponseCode();
            if (responseCode == 404) {
                return false;
            } else {
                String content = new String(connection.getInputStream().readAllBytes());
                return content.matches("(?s).*>\\s+" + version + ".*");
            }
        } catch (IOException e) {
            System.err.println("Error checking SDKMan: " + e.getMessage());
            return false;
        }
    }
}
