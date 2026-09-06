package dev.nirvik.telemetry;

/**
 * Immutable snapshot of vehicle telemetry.
 *
 * <p>Every field is final and the only way to change anything is to build a new instance via one of
 * the {@code with*} methods. That is what lets the network thread publish a state to the UI thread
 * without any locking: the object a reader sees can never be mutated underneath it.
 */

public final class TelemetryState {

    public final double latitude;
    public final double longitude;
    public final double relativeAltitudeM;
    public final double headingDeg;
    public final long positionAtMs;

    public final double rollDeg;
    public final double pitchDeg;
    public final long attitudeAtMs;

    public final double batteryVolts;
    public final int batteryPercent;
    public final long powerAtMs;

    public final String flightMode;
    public final boolean armed;
    public final long statusAtMs;

    public static final TelemetryState EMPTY = new TelemetryState(
            0, 0, 0, 0, 0,
            0, 0, 0,
            0, -1, 0,
            "UNKNOWN", false, 0);

    public TelemetryState(double latitude, double longitude, double relativeAltitudeM,
                          double headingDeg, long positionAtMs,
                          double rollDeg, double pitchDeg, long attitudeAtMs,
                          double batteryVolts, int batteryPercent, long powerAtMs,
                          String flightMode, boolean armed, long statusAtMs) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.relativeAltitudeM = relativeAltitudeM;
        this.headingDeg = headingDeg;
        this.positionAtMs = positionAtMs;
        this.rollDeg = rollDeg;
        this.pitchDeg = pitchDeg;
        this.attitudeAtMs = attitudeAtMs;
        this.batteryVolts = batteryVolts;
        this.batteryPercent = batteryPercent;
        this.powerAtMs = powerAtMs;
        this.flightMode = flightMode;
        this.armed = armed;
        this.statusAtMs = statusAtMs;
    }

    public TelemetryState withPosition(double lat, double lon, double altM,
                                       double hdg, long atMs) {
        return new TelemetryState(lat, lon, altM, hdg, atMs,
                rollDeg, pitchDeg, attitudeAtMs,
                batteryVolts, batteryPercent, powerAtMs,
                flightMode, armed, statusAtMs);
    }

    public TelemetryState withAttitude(double roll, double pitch, long atMs) {
        return new TelemetryState(latitude, longitude, relativeAltitudeM,
                headingDeg, positionAtMs,
                roll, pitch, atMs,
                batteryVolts, batteryPercent, powerAtMs,
                flightMode, armed, statusAtMs);
    }

    public TelemetryState withPower(double volts, int percent, long atMs) {
        return new TelemetryState(latitude, longitude, relativeAltitudeM,
                headingDeg, positionAtMs,
                rollDeg, pitchDeg, attitudeAtMs,
                volts, percent, atMs,
                flightMode, armed, statusAtMs);
    }

    public TelemetryState withStatus(String mode, boolean isArmed, long atMs) {
        return new TelemetryState(latitude, longitude, relativeAltitudeM,
                headingDeg, positionAtMs,
                rollDeg, pitchDeg, attitudeAtMs,
                batteryVolts, batteryPercent, powerAtMs,
                mode, isArmed, atMs);
    }

    public Freshness positionFreshness(long nowMs) {
        return FreshnessPolicy.POSITION.evaluate(positionAtMs, nowMs);
    }

    public Freshness attitudeFreshness(long nowMs) {
        return FreshnessPolicy.ATTITUDE.evaluate(attitudeAtMs, nowMs);
    }

    public Freshness powerFreshness(long nowMs) {
        return FreshnessPolicy.POWER.evaluate(powerAtMs, nowMs);
    }

    public Freshness statusFreshness(long nowMs) {
        return FreshnessPolicy.STATUS.evaluate(statusAtMs, nowMs);
    }
}