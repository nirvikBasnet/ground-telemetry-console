package dev.nirvik.telemetry;
import java.util.Random;

public final class Backoff {

    private final long baseMs;
    private final long maxMs;
    private final Random random;
    private int attempt = 0;

    public Backoff() {
        this(1_000, 30_000, new Random());
    }

    public Backoff(long baseMs, long maxMs, Random random) {
        this.baseMs = baseMs;
        this.maxMs = maxMs;
        this.random = random;
    }

    public long nextDelayMs() {
        long raw = Math.min(maxMs, baseMs * (1L << Math.min(attempt, 20)));
        attempt++;
        long half = raw / 2;
        return half + (long) (random.nextDouble() * half);
    }

    public void reset() {
        attempt = 0;
    }

    public int attempt() {
        return attempt;
    }
}