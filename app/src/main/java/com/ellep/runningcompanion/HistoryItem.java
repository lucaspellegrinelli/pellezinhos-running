package com.ellep.runningcompanion;

import org.json.JSONException;
import org.json.JSONObject;

public class HistoryItem {
    private long when;
    private long time;
    private double distance;
    private double pace;
    private double altimetry;

    public HistoryItem(long when, long time, double distance, double pace, double altimetry) {
        this.when = when;
        this.time = time;
        this.distance = distance;
        this.pace = pace;
        this.altimetry = altimetry;
    }

    public HistoryItem(JSONObject obj) {
        try {
            this.when = obj.getLong("when");
            this.time = obj.getLong("time");
            this.distance = obj.getDouble("distance");
            this.pace = obj.getDouble("pace");
            this.altimetry = obj.getDouble("altimetry");
        } catch (JSONException error) {
            System.out.println(error);
            this.when = 0;
            this.time = 0;
            this.distance = 0;
            this.pace = 0;
            this.altimetry = 0;
        }
    }

    public long getTime() {
        return time;
    }

    public double getDistance() {
        return distance;
    }

    public double getPace() {
        return pace;
    }

    public long getWhen() {
        return when;
    }

    public double getAltimetry() {
        return altimetry;
    }
}
