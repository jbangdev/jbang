//usr/bin/env jbang "$0" "$@" ; exit $?

// This example tests the time taken in ms to resolve localhostname.
// Original idea: https://github.com/thoeni/inetTester
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
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

    static class NanoClock extends Clock
    {
        private final Clock clock;

        private final long initialNanos;

        private final Instant initialInstant;

        public NanoClock()
        {
            this(Clock.systemUTC());
        }

        public NanoClock(final Clock clock)
        {
            this.clock = clock;
            initialInstant = clock.instant();
            initialNanos = getSystemNanos();
        }

        @Override
        public ZoneId getZone()
        {
            return clock.getZone();
        }

        @Override
        public Instant instant()
        {
            return initialInstant.plusNanos(getSystemNanos() - initialNanos);
        }

        @Override
        public Clock withZone(final ZoneId zone)
        {
            return new NanoClock(clock.withZone(zone));
        }

        private long getSystemNanos()
        {
            return System.nanoTime();
        }
    }

}
