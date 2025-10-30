///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS info.picocli:picocli:4.7.5
//DEPS com.fasterxml.jackson.core:jackson-databind:2.15.2
//DEPS org.jsoup:jsoup:1.15.4
//DEPS org.apache.httpcomponents.client5:httpclient5:5.2.1
//DEPS org.slf4j:slf4j-simple:1.7.36
//DOCS jbang_release_checker.md
//JAVA 21+

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import java.util.concurrent.Callable;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.io.BufferedReader;
import java.io.InputStreamReader;

@Command(name = "jbang-release-checker", 
         mixinStandardHelpOptions = true, 
         version = "1.0",
         description = "Check availability of JBang releases across all platforms")
public class jbang_release_checker implements Callable<Integer> {

    @Option(names = {"-v", "--version"}, description = "Specific JBang version to check (default: latest)")
    private String version;

    @Option(names = {"-t", "--timeout"}, description = "Timeout in seconds for HTTP requests (default: 10)")
    private int timeout = 10;

    @Option(names = {"--json"}, description = "Output results in JSON format")
    private boolean jsonOutput = false;

    @Option(names = {"--github-token"}, description = "GitHub token for GHCR authentication", defaultValue = "${GITHUB_TOKEN}")
    private String githubToken;

    private final ObjectMapper mapper = new ObjectMapper();
    private final CloseableHttpClient httpClient = HttpClients.createDefault();
    private final ExecutorService executor = createExecutor();

    private ExecutorService createExecutor() {
        try {
            // Try to use virtual threads (Java 21+)
            return Executors.newVirtualThreadPerTaskExecutor();
        } catch (NoSuchMethodError e) {
            // Fallback to platform threads for older Java versions
            return Executors.newFixedThreadPool(10);
        }
    }

    private String getGitHubToken() {
        // First try command line option
        if (githubToken != null && !githubToken.isEmpty()) {
            return githubToken;
        }
        
        // Then try environment variable
        String envToken = System.getenv("GITHUB_TOKEN");
        if (envToken != null && !envToken.isEmpty()) {
            return envToken;
        }
        
        // Finally try GitHub CLI
        try {
            Process process = new ProcessBuilder("gh", "auth", "token")
                .redirectErrorStream(true)
                .start();
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String token = reader.readLine();
                if (token != null && !token.trim().isEmpty()) {
                    return token.trim();
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                return null;
            }
        } catch (Exception e) {
            // GitHub CLI not available or failed
        }
        
        return null;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new jbang_release_checker()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        try {
            String targetVersion = version != null ? version : getLatestVersion();
            System.out.println("🔍 Checking JBang release availability for version: " + targetVersion);
            System.out.println("=".repeat(60));

            List<CheckResult> results = new ArrayList<>();
            
            // Submit all checks to virtual threads
            var futures = List.of(
                executor.submit(() -> checkGitHubReleases("jbangdev/jbang", targetVersion)),
                executor.submit(() -> checkSDKMAN("jbang",targetVersion)),
                executor.submit(() -> checkMavenCentral("dev.jbang", "jbang.bin", targetVersion)),
                executor.submit(() -> checkDockerHub("jbangdev", "jbang-action", targetVersion)),
                executor.submit(() -> checkGHCR("jbangdev", "jbang-action", targetVersion)),
                executor.submit(() -> checkHomebrew("jbang", targetVersion)),
                executor.submit(() -> checkChocolatey("jbang", targetVersion)),
                executor.submit(() -> checkScoop("ScoopInstaller/Main", "jbang", targetVersion)),
                executor.submit(() -> checkScoop("jbangdev/scoop-bucket", "jbang", targetVersion))
            );
            
            // Collect results with timeout
            for (var future : futures) {
                try {
                    results.add(future.get(timeout, TimeUnit.SECONDS));
                } catch (Exception e) {
                    results.add(new CheckResult("Unknown", false, "Error: " + e.getMessage()));
                }
            }

            if (jsonOutput) {
                outputJson(results);
            } else {
                outputText(results);
            }

            long successCount = results.stream().filter(r -> r.available).count();
            System.out.println("\n📊 Summary: " + successCount + "/" + results.size() + " platforms available");
            
            return successCount == results.size() ? 0 : 1;

        } finally {
            httpClient.close();
            executor.shutdown();
        }
    }

    private String getLatestVersion() throws Exception {
        String url = "https://api.github.com/repos/jbangdev/jbang/releases/latest";
        HttpGet request = new HttpGet(url);
        request.setHeader("Accept", "application/vnd.github.v3+json");
        
        String response = httpClient.execute(request, httpResponse -> 
            EntityUtils.toString(httpResponse.getEntity()));
        
        JsonNode node = mapper.readTree(response);
        return node.get("tag_name").asText().replace("v", "");
    }

    private CheckResult checkGitHubReleases(String repo, String version) {
        try {
            String url = "https://api.github.com/repos/" + repo + "/releases/tags/v" + version;
            HttpGet request = new HttpGet(url);
            request.setHeader("Accept", "application/vnd.github.v3+json");
            
            String response = httpClient.execute(request, httpResponse -> 
                EntityUtils.toString(httpResponse.getEntity()));
            
            JsonNode node = mapper.readTree(response);
            boolean available = !node.has("message") || !node.get("message").asText().equals("Not Found");
            
            return new CheckResult("GitHub Releases", available, 
                available ? "✅ Available" : "❌ Not found");
        } catch (Exception e) {
            return new CheckResult("GitHub Releases", false, "❌ Error: " + e.getMessage());
        }
    }

    private CheckResult checkSDKMAN(String candidate, String version) {
        try {
            String url = "https://api.sdkman.io/2/candidates/" + candidate + "/windows/versions/list";
            HttpGet request = new HttpGet(url);
            
            String response = httpClient.execute(request, httpResponse -> 
                EntityUtils.toString(httpResponse.getEntity()));
            
                        // Parse the text-based version list
            boolean available = false;
            String[] lines = response.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (!line.isEmpty()) {
                    // Extract any non-whitespace sections and check if any match the version
                    String[] parts = line.split("\\s+");
                    for (String part : parts) {
                        part = part.trim();
                        if (!part.isEmpty()) {
                            if (part.equals(version)) {
                                available = true;
                                break;
                            }
                        }
                    }
                    if (available) break;
                }
            }
            
            return new CheckResult("SDKMAN", available, 
                available ? "✅ Available" : "❌ Not found");
        } catch (Exception e) {
            return new CheckResult("SDKMAN", false, "❌ Error: " + e.getMessage());
        }
    }

    private CheckResult checkMavenCentral(String groupId, String artifactId, String version) {
        try {
            String url = "https://repo1.maven.org/maven2/" + groupId.replace('.', '/') + "/" + artifactId + "/maven-metadata.xml";
            HttpGet request = new HttpGet(url);
            
            String response = httpClient.execute(request, httpResponse -> 
                EntityUtils.toString(httpResponse.getEntity()));
            
            // Parse XML to find versions
            boolean available = false;
            String[] lines = response.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("<version>") && line.endsWith("</version>")) {
                    String foundVersion = line.substring(9, line.length() - 10); // Remove <version> and </version>
                    // System.out.println("Found Maven version: " + foundVersion + " (looking for: " + version + ")");
                    if (foundVersion.equals(version)) {
                        available = true;
                        break;
                    }
                }
            }
            
            return new CheckResult("Maven Central", available, 
                available ? "✅ Available" : "❌ Not found");
        } catch (Exception e) {
            return new CheckResult("Maven Central", false, "❌ Error: " + e.getMessage());
        }
    }

    private CheckResult checkDockerHub(String org, String repo, String version) {
        try {
            String url = "https://hub.docker.com/v2/repositories/" + org + "/" + repo + "/tags/" + version;
            HttpGet request = new HttpGet(url);
            
            int statusCode = httpClient.execute(request, httpResponse -> {
                return httpResponse.getCode();
            });
            
            boolean available = statusCode == 200;
            
            return new CheckResult("Docker Hub", available, 
                available ? "✅ Available" : "❌ Not found");
        } catch (Exception e) {
            return new CheckResult("Docker Hub", false, "❌ Error: " + e.getMessage());
        }
    }

    private CheckResult checkGHCR(String org, String packageName, String version) {
        try {
            // Use GitHub API to check package versions
            String url = "https://api.github.com/orgs/" + org + "/packages/container/" + packageName + "/versions";
            HttpGet request = new HttpGet(url);
            request.setHeader("Accept", "application/vnd.github.v3+json");
            
            // Add authentication if token is provided (required for GitHub Packages API)
            String token = getGitHubToken();
            if (token != null && !token.isEmpty()) {
                request.setHeader("Authorization", "Bearer " + token);
            } else {
                return new CheckResult("GitHub Container Registry", false, 
                    "❌ Requires authentication (use --github-token, set GITHUB_TOKEN env var, or run 'gh auth login')");
            }
            
            String response = httpClient.execute(request, httpResponse -> 
                EntityUtils.toString(httpResponse.getEntity()));
            
            JsonNode root = mapper.readTree(response);
            
            // Check for authentication error
            if (root.has("message")) {
                String message = root.get("message").asText();
                if (message.equals("Requires authentication")) {
                    return new CheckResult("GitHub Container Registry", false, 
                        "❌ Requires authentication (use --github-token or set GITHUB_TOKEN env var)");
                } else if (message.contains("read:packages scope")) {
                    return new CheckResult("GitHub Container Registry", false, 
                        "❌ Token needs 'read:packages' scope (run 'gh auth refresh --scopes read:packages')");
                }
            }
            
            boolean available = false;
            if (root.isArray()) {
                for (JsonNode versionNode : root) {
                    JsonNode metadata = versionNode.get("metadata");
                    if (metadata != null) {
                        JsonNode container = metadata.get("container");
                        if (container != null) {
                            JsonNode tags = container.get("tags");
                            if (tags != null && tags.isArray()) {
                                for (JsonNode tag : tags) {
                                    if (version.equals(tag.asText())) {
                                        available = true;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    if (available) break;
                }
            }
            
            return new CheckResult("GitHub Container Registry", available, 
                available ? "✅ Available" : "❌ Not found");
        } catch (Exception e) {
            return new CheckResult("GitHub Container Registry", false, "❌ Error: " + e.getMessage());
        }
    }

    private CheckResult checkHomebrew(String formula, String version) {
        try {
            String url = "https://formulae.brew.sh/api/formula/" + formula + ".json";
            HttpGet request = new HttpGet(url);
            
            String response = httpClient.execute(request, httpResponse -> 
                EntityUtils.toString(httpResponse.getEntity()));
            
            JsonNode node = mapper.readTree(response);
            String currentVersion = node.get("versions").get("stable").asText();
            boolean available = currentVersion.equals(version);
            
            return new CheckResult("Homebrew", available, 
                available ? "✅ Available (v" + currentVersion + ")" : "❌ Version mismatch (current: " + currentVersion + ")");
        } catch (Exception e) {
            return new CheckResult("Homebrew", false, "❌ Error: " + e.getMessage());
        }
    }

    private CheckResult checkChocolatey(String packageName, String version) {
        try {
            String url = "https://chocolatey.org/packages/" + packageName;
            Document doc = Jsoup.connect(url).timeout(timeout * 1000).get();
            
            String pageText = doc.text();
            boolean available = pageText.contains(version);
            
            return new CheckResult("Chocolatey", available, 
                available ? "✅ Available" : "❌ Not found");
        } catch (Exception e) {
            return new CheckResult("Chocolatey", false, "❌ Error: " + e.getMessage());
        }
    }

    private CheckResult checkScoop(String bucket, String packageName, String version) {
        try {
            String url;
            url = "https://raw.githubusercontent.com/" + bucket + "/HEAD/bucket/" + packageName + ".json";
            
            
            HttpGet request = new HttpGet(url);
            String response = httpClient.execute(request, httpResponse -> 
                EntityUtils.toString(httpResponse.getEntity()));
            JsonNode node = mapper.readTree(response);
            String scoopVersion = node.get("version").asText();
            boolean available = scoopVersion.equals(version);
            
            String bucketName = bucket.contains("/") ? bucket.split("/")[0] : bucket;
            return new CheckResult("Scoop (" + bucketName + ")", available, 
                available ? "✅ Available (v" + scoopVersion + ")" : "❌ Version mismatch (current: " + scoopVersion + ")");
        } catch (Exception e) {
            String bucketName = bucket.contains("/") ? bucket.split("/")[1] : bucket;
            return new CheckResult("Scoop (" + bucketName + ")", false, "❌ Error: " + e.getMessage());
        }
    }

    private void outputText(List<CheckResult> results) {
        for (CheckResult result : results) {
            System.out.printf("%-25s %s%n", result.platform, result.message);
        }
    }

    private void outputJson(List<CheckResult> results) throws Exception {
        Map<String, Object> output = new HashMap<>();
        output.put("version", version != null ? version : "latest");
        output.put("timestamp", System.currentTimeMillis());
        output.put("results", results);
        
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(output));
    }

    static class CheckResult {
        public final String platform;
        public final boolean available;
        public final String message;

        public CheckResult(String platform, boolean available, String message) {
            this.platform = platform;
            this.available = available;
            this.message = message;
        }
    }
} 