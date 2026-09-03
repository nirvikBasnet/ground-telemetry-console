package dev.nirvik.telemetry;

import io.dronefleet.mavlink.MavlinkConnection;
import io.dronefleet.mavlink.MavlinkMessage;
import io.dronefleet.mavlink.common.Attitude;
import io.dronefleet.mavlink.common.GlobalPositionInt;
import io.dronefleet.mavlink.common.SysStatus;
import io.dronefleet.mavlink.minimal.Heartbeat;
import io.dronefleet.mavlink.minimal.MavAutopilot;
import io.dronefleet.mavlink.minimal.MavModeFlag;
import io.dronefleet.mavlink.minimal.MavType;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Owns the TCP socket to MAVProxy and turns the MAVLink stream into {@link TelemetryState}
 * snapshots. All callbacks are delivered on the read thread; the caller is responsible for
 * marshalling to whatever thread it needs.
 */
public class MavlinkClient {

    public interface Listener {
        void onState(TelemetryState state);

        void onLog(String message);
    }

    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int HEARTBEAT_INTERVAL_MS = 1000;

    /** GCS convention: system 255, component 190 (MAV_COMP_ID_MISSIONPLANNER). */
    private static final int GCS_SYSTEM_ID = 255;
    private static final int GCS_COMPONENT_ID = 190;

    private final String host;
    private final int port;
    private final Listener listener;

    private volatile boolean running;
    private volatile Socket socket;
    private volatile MavlinkConnection connection;
    private Thread readThread;
    private Thread heartbeatThread;

    private TelemetryState state = TelemetryState.EMPTY;

    public MavlinkClient(String host, int port, Listener listener) {
        this.host = host;
        this.port = port;
        this.listener = listener;
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        readThread = new Thread(this::runReadLoop, "mavlink-read");
        readThread.start();
    }

    public void stop() {
        running = false;
        // Closing the socket is what unblocks connection.next() inside the read loop.
        Socket s = socket;
        if (s != null) {
            try {
                s.close();
            } catch (IOException ignored) {
                // Nothing useful to do; the read loop is going to exit either way.
            }
        }
    }

    private void runReadLoop() {
        try {
            listener.onLog("Connecting to " + host + ":" + port + "...");
            Socket s = new Socket();
            s.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            socket = s;

            InputStream in = new BufferedInputStream(s.getInputStream());
            OutputStream out = s.getOutputStream();
            connection = MavlinkConnection.create(in, out);
            listener.onLog("Connected.");

            heartbeatThread = new Thread(this::runHeartbeatLoop, "mavlink-heartbeat");
            heartbeatThread.start();

            MavlinkMessage<?> message;
            while (running && (message = connection.next()) != null) {
                handle(message.getPayload());
            }
        } catch (IOException e) {
            if (running) {
                listener.onLog("Connection error: " + e.getMessage());
            }
        } finally {
            Socket s = socket;
            if (s != null) {
                try {
                    s.close();
                } catch (IOException ignored) {
                    // Already tearing down.
                }
            }
            socket = null;
            connection = null;
            running = false;
            listener.onLog("Disconnected.");
        }
    }

    private void runHeartbeatLoop() {
        // ArduPilot stops streaming to a GCS that goes quiet, so this has to keep ticking
        // independently of whatever the read loop is doing.
        Heartbeat heartbeat =
                Heartbeat.builder()
                        .type(MavType.MAV_TYPE_GCS)
                        .autopilot(MavAutopilot.MAV_AUTOPILOT_INVALID)
                        .mavlinkVersion(3)
                        .build();
        while (running) {
            MavlinkConnection c = connection;
            if (c != null) {
                try {
                    c.send2(GCS_SYSTEM_ID, GCS_COMPONENT_ID, heartbeat);
                } catch (IOException e) {
                    if (running) {
                        listener.onLog("Heartbeat failed: " + e.getMessage());
                    }
                    return;
                }
            }
            try {
                Thread.sleep(HEARTBEAT_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * The live stream carries roughly 30 message types at ~4 Hz each. Everything outside the four
     * types below is dropped here, before any state is built.
     */
    private void handle(Object payload) {
        if (!(payload instanceof GlobalPositionInt)
                && !(payload instanceof Attitude)
                && !(payload instanceof SysStatus)
                && !(payload instanceof Heartbeat)) {
            return;
        }

        long now = System.currentTimeMillis();

        if (payload instanceof GlobalPositionInt) {
            GlobalPositionInt p = (GlobalPositionInt) payload;
            // lat/lon are degrees * 1e7, relativeAlt is mm above home,
            // vx/vy are cm/s, hdg is centidegrees.
            double groundSpeedMs = Math.hypot(p.vx(), p.vy()) / 100.0;
            state =
                    state.withPosition(
                            p.lat() / 1e7,
                            p.lon() / 1e7,
                            p.relativeAlt() / 1000.0,
                            groundSpeedMs,
                            p.hdg() / 100.0,
                            now);
        } else if (payload instanceof Attitude) {
            Attitude a = (Attitude) payload;
            // roll/pitch arrive in radians.
            state = state.withAttitude(Math.toDegrees(a.roll()), Math.toDegrees(a.pitch()), now);
        } else if (payload instanceof SysStatus) {
            SysStatus s = (SysStatus) payload;
            // voltageBattery is millivolts.
            state = state.withPower(s.voltageBattery() / 1000.0, s.batteryRemaining(), now);
        } else {
            Heartbeat h = (Heartbeat) payload;
            boolean armed =
                    h.baseMode() != null
                            && h.baseMode().flagsEnabled(MavModeFlag.MAV_MODE_FLAG_SAFETY_ARMED);
            state = state.withStatus(flightModeName(h.customMode()), armed, now);
        }

        listener.onState(state);
    }

    /** ArduCopter custom mode numbers. */
    private static String flightModeName(long customMode) {
        switch ((int) customMode) {
            case 0:
                return "STABILIZE";
            case 2:
                return "ALT_HOLD";
            case 3:
                return "AUTO";
            case 4:
                return "GUIDED";
            case 5:
                return "LOITER";
            case 6:
                return "RTL";
            case 7:
                return "CIRCLE";
            case 9:
                return "LAND";
            default:
                return "MODE_" + customMode;
        }
    }
}
