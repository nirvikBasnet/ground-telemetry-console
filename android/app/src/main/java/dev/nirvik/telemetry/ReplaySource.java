package dev.nirvik.telemetry;
import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import io.dronefleet.mavlink.MavlinkConnection;
import io.dronefleet.mavlink.MavlinkMessage;
import io.dronefleet.mavlink.common.Attitude;
import io.dronefleet.mavlink.common.GlobalPositionInt;

public final class ReplaySource implements TelemetrySource {

    private static final String TAG = "ReplaySource";
    private static final String ASSET = "sitl-session.tlog";

    private final Context context;
    private final Clock clock;
    private final MavlinkParser parser;
    private final LinkMonitor monitor = new LinkMonitor();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile boolean open = false;
    private Listener listener;
    private Thread thread;

    public ReplaySource(Context context, Clock clock) {
        this.context = context.getApplicationContext();
        this.clock = clock;
        this.parser = new MavlinkParser(clock);
    }

    @Override
    public void start(Listener listener) {
        this.listener = listener;
        if (!running.compareAndSet(false, true)) return;
        thread = new Thread(this::loop, "replay");
        thread.start();
    }

    @Override
    public void stop() {
        running.set(false);
        if (thread != null) thread.interrupt();
    }

    private void loop() {
        while (running.get()) {
            try (InputStream in = context.getAssets().open(ASSET)) {
                open = true;
                parser.resetOrdering();
                log("replaying " + ASSET);

                MavlinkConnection connection =
                        MavlinkConnection.create(in, new ByteArrayOutputStream());

                long previousBootMs = -1;

                while (running.get()) {
                    MavlinkMessage<?> message;
                    try {
                        message = connection.next();
                    } catch (IOException eof) {
                        break;
                    }
                    if (message == null) break;

                    Object payload = message.getPayload();
                    long bootMs = bootMsOf(payload);

                    if (bootMs >= 0) {
                        if (previousBootMs >= 0) {
                            long delta = bootMs - previousBootMs;
                            if (delta > 0 && delta < 2_000) {
                                Thread.sleep(delta);
                            }
                        }
                        previousBootMs = bootMs;
                    }

                    parser.apply(payload);
                }
                log("replay complete, looping");
            } catch (InterruptedException e) {
                return;
            } catch (IOException e) {
                Log.e(TAG, "replay failed", e);
                log("replay failed: " + e.getMessage());
                return;
            } finally {
                open = false;
            }
        }
    }

    private static long bootMsOf(Object payload) {
        if (payload instanceof GlobalPositionInt) {
            return ((GlobalPositionInt) payload).timeBootMs();
        }
        if (payload instanceof Attitude) {
            return ((Attitude) payload).timeBootMs();
        }
        return -1;
    }

    private void log(String message) {
        Log.i(TAG, message);
        if (listener != null) listener.onLog(message);
    }

    @Override public TelemetryState state() { return parser.state(); }

    @Override public LinkState linkState(long nowMs) {
        return monitor.evaluate(open, parser.lastHeartbeatAtMs(), nowMs);
    }

    @Override public long rejectedCount() { return parser.rejectedCount(); }

    @Override public long nextRetryInMs(long nowMs) { return 0; }

    @Override public String name() { return "REPLAY " + ASSET; }
}