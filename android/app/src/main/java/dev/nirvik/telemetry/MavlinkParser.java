package dev.nirvik.telemetry;

import io.dronefleet.mavlink.common.Attitude;
import io.dronefleet.mavlink.common.GlobalPositionInt;
import io.dronefleet.mavlink.common.SysStatus;
import io.dronefleet.mavlink.minimal.Heartbeat;
import io.dronefleet.mavlink.minimal.MavModeFlag;

public final class MavlinkParser {

    private final Clock clock;

    private volatile TelemetryState state = TelemetryState.EMPTY;
    private volatile long lastHeartbeatAtMs = 0;
    private volatile long rejectedCount = 0;
    private volatile long acceptedCount = 0;

    private long lastPositionBootMs = -1;
    private long lastAttitudeBootMs = -1;

    public MavlinkParser(Clock clock) {
        this.clock = clock;
    }

    public boolean apply(Object payload) {
        long now = clock.elapsedRealtime();

        if (payload instanceof GlobalPositionInt) {
            GlobalPositionInt p = (GlobalPositionInt) payload;
            long boot = p.timeBootMs();
            if (boot < lastPositionBootMs) {
                rejectedCount++;
                return false;
            }
            lastPositionBootMs = boot;
            state = state.withPosition(
                    p.lat() / 1e7,
                    p.lon() / 1e7,
                    p.relativeAlt() / 1000.0,
                    p.hdg() / 100.0,
                    now);

        } else if (payload instanceof Attitude) {
            Attitude a = (Attitude) payload;
            long boot = a.timeBootMs();
            if (boot < lastAttitudeBootMs) {
                rejectedCount++;
                return false;
            }
            lastAttitudeBootMs = boot;
            state = state.withAttitude(
                    Math.toDegrees(a.roll()),
                    Math.toDegrees(a.pitch()),
                    now);

        } else if (payload instanceof SysStatus) {
            SysStatus s = (SysStatus) payload;
            state = state.withPower(
                    s.voltageBattery() / 1000.0,
                    s.batteryRemaining(),
                    now);

        } else if (payload instanceof Heartbeat) {
            Heartbeat h = (Heartbeat) payload;
            lastHeartbeatAtMs = now;
            boolean armed = h.baseMode()
                    .flagsEnabled(MavModeFlag.MAV_MODE_FLAG_SAFETY_ARMED);
            state = state.withStatus(modeName(h.customMode()), armed, now);

        } else {
            return false;
        }

        acceptedCount++;
        return true;
    }

    public TelemetryState state() { return state; }
    public long lastHeartbeatAtMs() { return lastHeartbeatAtMs; }
    public long rejectedCount() { return rejectedCount; }
    public long acceptedCount() { return acceptedCount; }

    public void resetOrdering() {
        lastPositionBootMs = -1;
        lastAttitudeBootMs = -1;
    }

    public static String modeName(long customMode) {
        switch ((int) customMode) {
            case 0: return "STABILIZE";
            case 2: return "ALT_HOLD";
            case 3: return "AUTO";
            case 4: return "GUIDED";
            case 5: return "LOITER";
            case 6: return "RTL";
            case 7: return "CIRCLE";
            case 9: return "LAND";
            default: return "MODE_" + customMode;
        }
    }
}
