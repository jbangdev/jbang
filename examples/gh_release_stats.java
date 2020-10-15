///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.5.0
//DEPS org.kohsuke:github-api:1.116

import org.kohsuke.github.GHAsset;
import org.kohsuke.github.GHRelease;
import org.kohsuke.github.GitHub;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.concurrent.Callable;

import static java.lang.System.out;

@Command(name = "gh_release_stats", mixinStandardHelpOptions = true, version = "gh_release_stats 0.1",
        description = "gh_release_stats made with jbang")
class gh_release_stats implements Callable<Integer> {

    @CommandLine.Option(names={"--token", "-t"}, description = "GitHub token", defaultValue = "${GITHUB_TOKEN}", required = true)
    private String token;

    @CommandLine.Option(names={"--repo", "-r"}, description = "GitHub Repository (<orgusername>/<reponame>)", required = true)
    private String repo;

    public static void main(String... args) {
        int exitCode = new CommandLine(new gh_release_stats()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        GitHub gh = GitHub.connect("", token);


        out.println("release date, release name, name, size, count");

        for (GHRelease release:gh.getRepository(repo).listReleases().toList()) {

            List<GHAsset> assets = release.getAssets();
            for(GHAsset asset : assets) {
                out.printf("%tF,%s,%s,%s,%s\n",
                        release.getPublished_at(),
                        release.getName(),
                        asset.getName(),
                        asset.getSize(),
                        asset.getDownloadCount());
            }
        }
        return 0;
    }
}
