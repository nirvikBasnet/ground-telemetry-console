package dev.nirvik.telemetry;

public final class FreshnessPolicy {

    public static final FreshnessPolicy POSITION = new FreshnessPolicy(1_000, 3_000);
    public static final FreshnessPolicy ATTITUDE = new FreshnessPolicy(1_000, 3_000);
    public static final FreshnessPolicy POWER    = new FreshnessPolicy(10_000, 30_000);
    public static final FreshnessPolicy STATUS   = new FreshnessPolicy(3_000, 10_000);

    private final long ageingAfterMs;
    private final long staleAfterMs;

    public FreshnessPolicy(long ageingAfterMs, long staleAfterMs) {
        this.ageingAfterMs = ageingAfterMs;
        this.staleAfterMs = staleAfterMs;
    }

    public Freshness evaluate(long updatedAtMs, long nowMs) {
        if (updatedAtMs <= 0) return Freshness.UNAVAILABLE;
        long age = nowMs - updatedAtMs;
        if (age <= ageingAfterMs) return Freshness.FRESH;
        if (age <= staleAfterMs) return Freshness.AGEING;
        return Freshness.STALE;
    }

    public long ageingAfterMs() { return ageingAfterMs; }
    public long staleAfterMs() { return staleAfterMs; }
}
