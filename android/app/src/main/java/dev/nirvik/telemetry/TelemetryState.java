package dev.nirvik.telemetry;

/**
 * Immutable snapshot of vehicle telemetry.
 *
 * <p>Every field is final and the only way to change anything is to build a new instance via one of
 * the {@code with*} methods. That is what lets the network thread publish a state to the UI thread
 * without any locking: the object a reader sees can never be mutated underneath it.
 */
public final class TelemetryState {

    public static final TelemetryState EMPTY =
            new TelemetryState(0, 0, 0, 0, 0, 0, 0, 0, 0, "UNKNOWN", false, 0);

    public final double latitude;
    public final double longitude;
    public final double relativeAltitudeM;
    public final double groundSpeedMs;
    public final double headingDeg;
    public final double rollDeg;
    public final double pitchDeg;
    public final double batteryVolts;
    public final int batteryPercent;
    public final String flightMode;
    public final boolean armed;
    public final long updatedAtMillis;

    private TelemetryState(
            double latitude,
            double longitude,
            double relativeAltitudeM,
            double groundSpeedMs,
            double headingDeg,
            double rollDeg,
            double pitchDeg,
            double batteryVolts,
            int batteryPercent,
            String flightMode,
            boolean armed,
            long updatedAtMillis) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.relativeAltitudeM = relativeAltitudeM;
        this.groundSpeedMs = groundSpeedMs;
        this.headingDeg = headingDeg;
        this.rollDeg = rollDeg;
        this.pitchDeg = pitchDeg;
        this.batteryVolts = batteryVolts;
        this.batteryPercent = batteryPercent;
        this.flightMode = flightMode;
        this.armed = armed;
        this.updatedAtMillis = updatedAtMillis;
    }

    public TelemetryState withPosition(
            double latitude,
            double longitude,
            double relativeAltitudeM,
            double groundSpeedMs,
            double headingDeg,
            long updatedAtMillis) {
        return new TelemetryState(
                latitude,
                longitude,
                relativeAltitudeM,
                groundSpeedMs,
                headingDeg,
                rollDeg,
                pitchDeg,
                batteryVolts,
                batteryPercent,
                flightMode,
                armed,
                updatedAtMillis);
    }

    public TelemetryState withAttitude(double rollDeg, double pitchDeg, long updatedAtMillis) {
        return new TelemetryState(
                latitude,
                longitude,
                relativeAltitudeM,
                groundSpeedMs,
                headingDeg,
                rollDeg,
                pitchDeg,
                batteryVolts,
                batteryPercent,
                flightMode,
                armed,
                updatedAtMillis);
    }

    public TelemetryState withPower(double batteryVolts, int batteryPercent, long updatedAtMillis) {
        return new TelemetryState(
                latitude,
                longitude,
                relativeAltitudeM,
                groundSpeedMs,
                headingDeg,
                rollDeg,
                pitchDeg,
                batteryVolts,
                batteryPercent,
                flightMode,
                armed,
                updatedAtMillis);
    }

    public TelemetryState withStatus(String flightMode, boolean armed, long updatedAtMillis) {
        return new TelemetryState(
                latitude,
                longitude,
                relativeAltitudeM,
                groundSpeedMs,
                headingDeg,
                rollDeg,
                pitchDeg,
                batteryVolts,
                batteryPercent,
                flightMode,
                armed,
                updatedAtMillis);
    }
}
