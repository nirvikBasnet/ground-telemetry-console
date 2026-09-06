package dev.nirvik.telemetry;

public final class LinkMonitor {

    private final long degradedAfterMs;
    private final long lostAfterMs;

    public LinkMonitor() {
        this(3_000, 6_000);
    }

    public LinkMonitor(long degradedAfterMs, long lostAfterMs) {
        this.degradedAfterMs = degradedAfterMs;
        this.lostAfterMs = lostAfterMs;
    }

    public LinkState evaluate(boolean socketOpen, long lastHeartbeatAtMs, long nowMs) {
        if (!socketOpen) return LinkState.LOST;
        if (lastHeartbeatAtMs <= 0) return LinkState.CONNECTING;
        long age = nowMs - lastHeartbeatAtMs;
        if (age <= degradedAfterMs) return LinkState.LIVE;
        if (age <= lostAfterMs) return LinkState.DEGRADED;
        return LinkState.LOST;
    }
}
