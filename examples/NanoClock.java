
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

public class NanoClock extends Clock
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
