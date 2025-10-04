///usr/bin/env jbang "$0" "$@" ; exit $?

//DEPS info.picocli:picocli:4.7.7
//DEPS com.fasterxml.jackson.core:jackson-core:2.20.0
//DEPS com.fasterxml.jackson.core:jackson-databind:2.20.0

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(name = "weather", mixinStandardHelpOptions = true, description = "Get weather information for a city")
class weather implements Runnable {

  @Parameters(index = "0", description = "City name")
  private String city;

  public static void main(String[] args) {
    new CommandLine(new weather()).execute(args);
  }

  @Override
  public void run() {
    try {
      String url = "https://wttr.in/" + city + "?format=j1";
      HttpClient client = HttpClient.newHttpClient();
      HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).build();

      HttpResponse<String> response = client.send(request,
          HttpResponse.BodyHandlers.ofString());

      // Simple output - in real app you'd parse the JSON
      System.out.println("Weather for " + city + ":");
      System.out.println("Raw data: " + response.body().substring(0, 200) + "...");
    } catch (Exception e) {
      System.err.println("Error fetching weather: " + e.getMessage());
    }
  }
}
