package dev.nirvik.telemetry;

import android.util.Log;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

import io.dronefleet.mavlink.MavlinkConnection;
import io.dronefleet.mavlink.MavlinkMessage;
import io.dronefleet.mavlink.minimal.Heartbeat;
import io.dronefleet.mavlink.minimal.MavAutopilot;
import io.dronefleet.mavlink.minimal.MavState;
import io.dronefleet.mavlink.minimal.MavType;

public final class MavlinkClient implements TelemetrySource {

    private static final String TAG = "MavlinkClient";
    private static final String HOST = "10.0.2.2";
    private static final int PORT = 14550;
    private static final int SYSTEM_ID = 255;
    private static final int COMPONENT_ID = 190;

    private final Clock clock;
    private final MavlinkParser parser;
    private final LinkMonitor monitor = new LinkMonitor();
    private final Backoff backoff = new Backoff();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile boolean socketOpen = false;
    private volatile long nextRetryAtMs = 0;
    private Listener listener;
    private Thread readThread;

    public MavlinkClient(Clock clock) {
        this.clock = clock;
        this.parser = new MavlinkParser(clock);
    }

    @Override
    public void start(Listener listener) {
        this.listener = listener;
        if (!running.compareAndSet(false, true)) return;
        readThread = new Thread(this::loop, "mavlink-read");
        readThread.start();
    }

    @Override
    public void stop() {
        running.set(false);
        if (readThread != null) readThread.interrupt();
    }

    private void loop() {
        while (running.get()) {
            Socket socket = null;
            Thread heartbeat = null;
            try {
                log("connecting to " + HOST + ":" + PORT);
                socket = new Socket();
                socket.connect(new InetSocketAddress(HOST, PORT), 5_000);
                socketOpen = true;
                backoff.reset();
                nextRetryAtMs = 0;
                parser.resetOrdering();
                log("connected");

                MavlinkConnection connection = MavlinkConnection.create(
                        socket.getInputStream(), socket.getOutputStream());
                heartbeat = startHeartbeat(connection);

                while (running.get()) {
                    MavlinkMessage<?> message = connection.next();
                    if (message == null) break;
                    parser.apply(message.getPayload());
                }
            } catch (IOException e) {
                Log.w(TAG, "link error", e);
                log("link error: " + e.getMessage());
            } finally {
                socketOpen = false;
                if (heartbeat != null) heartbeat.interrupt();
                closeQuietly(socket);
            }

            if (!running.get()) break;

            long delay = backoff.nextDelayMs();
            nextRetryAtMs = clock.elapsedRealtime() + delay;
            log("reconnecting in " + delay + "ms (attempt " + backoff.attempt() + ")");
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                return;
            }
        }
        log("stopped");
    }

    private Thread startHeartbeat(MavlinkConnection connection) {
        Thread t = new Thread(() -> {
            Heartbeat beat = Heartbeat.builder()
                    .type(MavType.MAV_TYPE_GCS)
                    .autopilot(MavAutopilot.MAV_AUTOPILOT_INVALID)
                    .systemStatus(MavState.MAV_STATE_UNINIT)
                    .mavlinkVersion(3)
                    .build();
            while (running.get() && socketOpen) {
                try {
                    connection.send2(SYSTEM_ID, COMPONENT_ID, beat);
                    Thread.sleep(1_000);
                } catch (InterruptedException e) {
                    return;
                } catch (IOException e) {
                    return;
                }
            }
        }, "mavlink-heartbeat");
        t.start();
        return t;
    }

    private void closeQuietly(Socket socket) {
        if (socket == null) return;
        try { socket.close(); } catch (IOException ignored) { }
    }

    private void log(String message) {
        Log.i(TAG, message);
        if (listener != null) listener.onLog(message);
    }

    @Override public TelemetryState state() { return parser.state(); }

    @Override public LinkState linkState(long nowMs) {
        return monitor.evaluate(socketOpen, parser.lastHeartbeatAtMs(), nowMs);
    }

    @Override public long rejectedCount() { return parser.rejectedCount(); }

    @Override public long nextRetryInMs(long nowMs) {
        return nextRetryAtMs == 0 ? 0 : Math.max(0, nextRetryAtMs - nowMs);
    }

    @Override public String name() { return "SITL " + HOST + ":" + PORT; }
}