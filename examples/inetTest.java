//usr/bin/env jbang "$0" "$@" ; exit $?
//SOURCES NanoClock.java

// This example tests the time taken in ms to resolve localhostname.
// Original idea: https://github.com/thoeni/inetTester
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

public class inetTest {

    public static void main(String[] args) throws UnknownHostException {
        final Clock clock = new NanoClock();
        final Instant startTime = Instant.now(clock);
        String hostName = InetAddress.getLocalHost().getHostName();
        final Instant endTime = Instant.now(clock);
        System.out.printf("hostname %s, elapsed time: %d (ms)%n", hostName, TimeUnit.NANOSECONDS.toMillis(Duration.between(startTime, endTime).toNanos()));
    }


}
