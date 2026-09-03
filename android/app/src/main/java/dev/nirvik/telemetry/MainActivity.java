package dev.nirvik.telemetry;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements MavlinkClient.Listener {

    /** The Docker host as seen from the Android emulator. */
    private static final String HOST = "10.0.2.2";
    private static final int PORT = 14550;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final SimpleDateFormat timeFormat =
            new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    private TextView telemetry;
    private MavlinkClient client;

    // Both only ever touched on the UI thread.
    private TelemetryState state = TelemetryState.EMPTY;
    private String lastLog = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        telemetry = findViewById(R.id.telemetry);
        render();

        client = new MavlinkClient(HOST, PORT, this);
        client.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        client.stop();
    }

    // MavlinkClient.Listener — both callbacks arrive on the network thread.

    @Override
    public void onState(TelemetryState newState) {
        handler.post(() -> {
            state = newState;
            render();
        });
    }

    @Override
    public void onLog(String message) {
        handler.post(() -> {
            lastLog = message;
            render();
        });
    }

    private void render() {
        TelemetryState s = state;
        StringBuilder sb = new StringBuilder();
        line(sb, "Mode", s.flightMode);
        line(sb, "Armed", s.armed ? "ARMED" : "DISARMED");
        line(sb, "Latitude", String.format(Locale.US, "%.7f", s.latitude));
        line(sb, "Longitude", String.format(Locale.US, "%.7f", s.longitude));
        line(sb, "Alt (rel)", String.format(Locale.US, "%.2f m", s.relativeAltitudeM));
        line(sb, "Ground speed", String.format(Locale.US, "%.2f m/s", s.groundSpeedMs));
        line(sb, "Heading", String.format(Locale.US, "%.1f deg", s.headingDeg));
        line(sb, "Roll", String.format(Locale.US, "%.1f deg", s.rollDeg));
        line(sb, "Pitch", String.format(Locale.US, "%.1f deg", s.pitchDeg));
        line(sb, "Battery", String.format(Locale.US, "%.2f V", s.batteryVolts));
        line(sb, "Remaining", s.batteryPercent + " %");
        line(sb, "Updated", s.updatedAtMillis == 0
                ? "-"
                : timeFormat.format(new Date(s.updatedAtMillis)));
        line(sb, "Status", lastLog);
        telemetry.setText(sb.toString());
    }

    private static void line(StringBuilder sb, String label, String value) {
        sb.append(String.format(Locale.US, "%-14s %s%n", label, value));
    }
}
