//usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA_OPTIONS --add-opens java.base/java.net=ALL-UNNAMED
//JAVA_OPTIONS --add-opens java.base/sun.net.www.protocol.https=ALL-UNNAMED
//DEPS info.picocli:picocli:4.5.0
//DEPS org.kohsuke:github-api:1.101

import org.kohsuke.github.GHAsset;
import org.kohsuke.github.GHRelease;
import org.kohsuke.github.GitHub;
import org.kohsuke.github.PagedIterator;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Command(name = "gh_fetch_release_assets", mixinStandardHelpOptions = true, version = "gh_fetch_release_assets 0.1",
        description = "Fetch latest release artifacts from a github repo with jbang")
class fetchlatestgraalvm implements Callable<Integer> {

    @Parameters(index = "0", description = "The repo to fetch latest release for", defaultValue = "graalvm/graalvm-ce-dev-builds")
    private String repo;

    @CommandLine.Option(names={"--assets"}, description="The asset pattern to look for.", defaultValue=".*")
    Pattern assetPattern;

    public static void main(String... args) {
        int exitCode = new CommandLine(new fetchlatestgraalvm()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception { // your business logic goes here...
        GitHub github = GitHub.connectAnonymously();

        var ghRepo = github.getRepository(repo);
        ghRepo.archive();

        PagedIterator<GHRelease> releases = ghRepo.listReleases().iterator();
        if(releases.hasNext()) {
            var release = releases.next();
            for(GHAsset asset: release.getAssets()) {
                if(assetPattern.matcher(asset.getName()).matches()) {
                    System.out.println(asset.getBrowserDownloadUrl());
                    //System.out.println(asset.getName());
                }
            }
        } else {
            System.err.println("No releases found.");
            return CommandLine.ExitCode.SOFTWARE;
        }

        return CommandLine.ExitCode.OK;
    }
}
