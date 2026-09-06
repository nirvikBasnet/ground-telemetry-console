package dev.nirvik.telemetry;

import android.os.SystemClock;

public class AndroidClock implements Clock{
    @Override
    public long elapsedRealtime() {
        return SystemClock.elapsedRealtime();
    }
}
