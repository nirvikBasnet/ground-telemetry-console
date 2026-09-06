package dev.nirvik.telemetry;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class MainActivity extends AppCompatActivity implements TelemetrySource.Listener {

    private static final long RENDER_INTERVAL_MS = 100;

    private TextView view;
    private Button toggle;
    private final Clock clock = new AndroidClock();
    private final Handler ui = new Handler(Looper.getMainLooper());

    private TelemetrySource source;
    private boolean replaying = false;
    private String lastLog = "";

    private final Runnable renderLoop = new Runnable() {
        @Override
        public void run() {
            render();
            ui.postDelayed(this, RENDER_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        view = findViewById(R.id.telemetry);
        toggle = findViewById(R.id.toggleSource);
        toggle.setOnClickListener(v -> switchSource());
        startSource(new MavlinkClient(clock));
    }

    @Override
    protected void onResume() {
        super.onResume();
        ui.post(renderLoop);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(renderLoop);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (source != null) source.stop();
    }

    private void switchSource() {
        if (source != null) source.stop();
        replaying = !replaying;
        toggle.setText(replaying ? "Switch to live" : "Switch to replay");
        startSource(replaying
                ? new ReplaySource(this, clock)
                : new MavlinkClient(clock));
    }

    private void startSource(TelemetrySource next) {
        source = next;
        source.start(this);
    }

    private void render() {
        if (source == null) return;
        long now = clock.elapsedRealtime();
        TelemetryState s = source.state();
        LinkState link = source.linkState(now);
        long retry = source.nextRetryInMs(now);

        StringBuilder b = new StringBuilder();
        b.append("SOURCE    ").append(source.name()).append('\n');
        b.append("LINK      ").append(link);
        if (link == LinkState.LOST && retry > 0) {
            b.append("  retry in ").append(retry / 1000).append('s');
        }
        b.append("\n\n");

        b.append(line("MODE", s.flightMode, s.statusAtMs, s.statusFreshness(now), now));
        b.append(line("ARMED", String.valueOf(s.armed), s.statusAtMs, s.statusFreshness(now), now));
        b.append(line("LAT", String.format(Locale.US, "%.6f", s.latitude),
                s.positionAtMs, s.positionFreshness(now), now));
        b.append(line("LON", String.format(Locale.US, "%.6f", s.longitude),
                s.positionAtMs, s.positionFreshness(now), now));
        b.append(line("ALT", String.format(Locale.US, "%.1f m", s.relativeAltitudeM),
                s.positionAtMs, s.positionFreshness(now), now));
        b.append(line("HDG", String.format(Locale.US, "%.0f deg", s.headingDeg),
                s.positionAtMs, s.positionFreshness(now), now));
        b.append(line("ROLL", String.format(Locale.US, "%.1f deg", s.rollDeg),
                s.attitudeAtMs, s.attitudeFreshness(now), now));
        b.append(line("PITCH", String.format(Locale.US, "%.1f deg", s.pitchDeg),
                s.attitudeAtMs, s.attitudeFreshness(now), now));
        b.append(line("BATT", String.format(Locale.US, "%.2f V (%d%%)",
                        s.batteryVolts, s.batteryPercent),
                s.powerAtMs, s.powerFreshness(now), now));

        b.append('\n');
        b.append("REJECTED  ").append(source.rejectedCount()).append(" out-of-order\n");
        b.append("LOG       ").append(lastLog);

        view.setText(b.toString());
    }

    private String line(String label, String value, long atMs,
                        Freshness freshness, long now) {
        String age = atMs <= 0 ? "--" : ((now - atMs) / 100 / 10.0) + "s";
        String marker;
        switch (freshness) {
            case FRESH:       marker = "   "; break;
            case AGEING:      marker = " ? "; break;
            case STALE:       marker = " ! "; break;
            default:          marker = " - "; break;
        }
        return String.format(Locale.US, "%-9s%s%-22s%8s  %s%n",
                label, marker, value, age, freshness);
    }

    @Override
    public void onLog(String message) {
        lastLog = message;
    }
}