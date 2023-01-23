package com.ellep.runningcompanion;

import android.location.Location;

import androidx.annotation.NonNull;

public class RunnerLocationReport {
    private Location location;
    private long time;
    private String source;

    public RunnerLocationReport(String source, long time, Location location) {
        this.location = location;
        this.time = time;
        this.source = source;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    @NonNull
    @Override
    public String toString() {
        return this.time + " " + this.source + " " + this.location;
    }
}
