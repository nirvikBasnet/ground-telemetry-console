package dev.nirvik.telemetry;

public interface TelemetrySource {

    interface Listener {
        void onLog(String message);
    }

    void start(Listener listener);
    void stop();

    TelemetryState state();
    LinkState linkState(long nowMs);
    long rejectedCount();
    long nextRetryInMs(long nowMs);
    String name();
}
