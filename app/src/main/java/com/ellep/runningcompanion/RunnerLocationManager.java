package com.ellep.runningcompanion;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;

import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RunnerLocationManager {
    private int location_permission_request_code;
    private List<String> locationProviders;
    private List<LocationListener> locationListeners =  new ArrayList<>();

    private List<RunnerLocationReport> locationReports = new ArrayList<>();

    public RunnerLocationManager(int location_permission_request_code, List<String> locationProviders) {
        this.locationProviders = locationProviders;
        this.location_permission_request_code = location_permission_request_code;
    }

    public void registerLocationListeners(Context context, Activity activity) {
        for (String provider : locationProviders) {
            boolean needsFineLocation = ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED;
            boolean needsCoarseLocation = ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED;

            if (needsFineLocation || needsCoarseLocation) {
                // If the permission is not granted, request it from the user
                ActivityCompat.requestPermissions(
                        activity,
                        new String[]{
                                android.Manifest.permission.ACCESS_FINE_LOCATION,
                                android.Manifest.permission.ACCESS_COARSE_LOCATION
                        },
                        location_permission_request_code
                );

                return;
            }

            LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            LocationListener locationListener = location -> {
                long currentTime = Calendar.getInstance().getTimeInMillis();
                RunnerLocationReport report = new RunnerLocationReport(provider, currentTime, location);
                addLocationReport(report);
            };

            locationManager.requestLocationUpdates(provider, 0, 0, locationListener);
            locationListeners.add(locationListener);
        }
    }

    public void unregisterLocationListeners(Context context) {
        for (LocationListener locationListener : locationListeners) {
            LocationManager locationManager = (LocationManager) context.getSystemService(context.LOCATION_SERVICE);
            locationManager.removeUpdates(locationListener);
        }
    }

    public void addLocationReport(RunnerLocationReport report) {
        locationReports.add(report);
    }

    public double getCurrentSpeed() {
        List<RunnerLocationReport> reports = getSpeedOptimizedSequence(0, true);
        if (reports.size() == 0) {
            return 0;
        }

        int startIndex = Math.max(0, reports.size() - 5);
        List<RunnerLocationReport> lastReports = reports.subList(startIndex, reports.size());

        double averageSpeed = 0;
        for (RunnerLocationReport report : lastReports) {
            averageSpeed += report.getLocation().getSpeed();
        }

        averageSpeed /= (double)lastReports.size();

        double secPerKm = 1000.0 / averageSpeed;
        return secPerKm / 60.0;
    }

    public double getDistanceTraveled(long since) {
        List<RunnerLocationReport> reports = getLocalizationOptimizedSequence(since);
        double distanceTraveled = 0;
        Location lastPosition = null;
        for (RunnerLocationReport report : reports) {
            if (lastPosition != null) {
                distanceTraveled += lastPosition.distanceTo(report.getLocation());
            }

            lastPosition = report.getLocation();
        }

        return distanceTraveled / 1000.0;
    }

    public double getAltitudeDistance(long since) {
        List<RunnerLocationReport> reports = getLocalizationOptimizedSequence(since);
        double totalAltitude = 0;
        Location lastLocation = null;
        for (RunnerLocationReport report : reports) {
            if (lastLocation != null) {
                totalAltitude += Math.abs(lastLocation.getAltitude() - report.getLocation().getAltitude());
            }

            lastLocation = report.getLocation();
        }

        return totalAltitude;
    }

    public double getOverallSpeed(long since) {
        double distanceKm = getDistanceTraveled(since);
        long currentTime = Calendar.getInstance().getTimeInMillis();
        long timeDiffMs = currentTime - since;
        double timeDiffMin = timeDiffMs / 60000.0;
        return timeDiffMin / distanceKm;
    }

    public Location getCurrentLocation() {
        List<RunnerLocationReport> reports = getLocalizationOptimizedSequence(0);
        if (reports.size() > 0)
            return reports.get(reports.size() - 1).getLocation();
        return null;
    }

    public long getElapsedTime(long since) {
        long currentTime = Calendar.getInstance().getTimeInMillis();
        long timeDiffMs = currentTime - since;
        return timeDiffMs / 1000;
    }

    public double getLastLocationAccuracy() {
        long currentTime = Calendar.getInstance().getTimeInMillis();
        long fiveSecAgo = currentTime - 5000;

        List<RunnerLocationReport> reports = getLocalizationOptimizedSequence(fiveSecAgo);
        if (reports.size() == 0) {
            return 0;
        }

        Location currentLocation = reports.get(reports.size() - 1).getLocation();

        if (currentLocation != null)
            return getCurrentLocation().getAccuracy();

        return 0;
    }

    public String getLastLocationSource() {
        long currentTime = Calendar.getInstance().getTimeInMillis();
        long fiveSecAgo = currentTime - 5000;

        List<RunnerLocationReport> reports = getLocalizationOptimizedSequence(fiveSecAgo);
        if (reports.size() == 0) {
            return "unknown";
        }

        Location currentLocation = reports.get(reports.size() - 1).getLocation();

        if (currentLocation != null)
            return getCurrentLocation().getProvider();

        return "unknown";
    }

    public boolean locationServiceConnected() {
        double lastAccuracy = getLastLocationAccuracy();
        return lastAccuracy > 0 && lastAccuracy <= 5;
    }

    public List<RunnerLocationReport> getLocalizationOptimizedSequence(long since) {
        HashMap<Long, List<RunnerLocationReport>> reportsPerSecond = new HashMap<>();

        for (RunnerLocationReport report : locationReports) {
            if (report.getLocation().hasAccuracy() && report.getTime() >= since) {
                long second = Math.round(report.getTime() / 1000.0);
                if (reportsPerSecond.containsKey(second)) {
                    reportsPerSecond.get(second).add(report);
                } else {
                    List<RunnerLocationReport> listOfReports = new ArrayList<>();
                    listOfReports.add(report);
                    reportsPerSecond.put(second, listOfReports);
                }
            }
        }

        HashMap<Long, RunnerLocationReport> bestReportPerSecond = new HashMap<>();
        for (Map.Entry<Long, List<RunnerLocationReport>> entry : reportsPerSecond.entrySet()) {
            Long key = entry.getKey();
            List<RunnerLocationReport> reports = entry.getValue();

            RunnerLocationReport reportBestLocation = reports.get(0);
            for (RunnerLocationReport report : reports) {
                if (reportBestLocation.getLocation().getAccuracy() > report.getLocation().getAccuracy()) {
                    reportBestLocation = report;
                }
            }

            bestReportPerSecond.put(key, reportBestLocation);
        }

        List<Long> keyList = new ArrayList<>(bestReportPerSecond.keySet());
        Collections.sort(keyList);

        List<RunnerLocationReport> output = new ArrayList<>();
        for (Long key : keyList) {
            output.add(bestReportPerSecond.get(key));
        }

        return output;
    }

    public List<RunnerLocationReport> getSpeedOptimizedSequence(long since, boolean nonZeroSpeed) {
        HashMap<Long, List<RunnerLocationReport>> reportsPerSecond = new HashMap<>();

        for (RunnerLocationReport report : locationReports) {
            boolean speedAllowed = !nonZeroSpeed || report.getLocation().getSpeed() > 0;
            if (report.getLocation().hasSpeedAccuracy() && report.getTime() >= since && speedAllowed) {
                long second = Math.round(report.getTime() / 1000.0);
                if (reportsPerSecond.containsKey(second)) {
                    reportsPerSecond.get(second).add(report);
                } else {
                    List<RunnerLocationReport> listOfReports = new ArrayList<>();
                    listOfReports.add(report);
                    reportsPerSecond.put(second, listOfReports);
                }
            }
        }

        HashMap<Long, RunnerLocationReport> bestReportPerSecond = new HashMap<>();
        for (Map.Entry<Long, List<RunnerLocationReport>> entry : reportsPerSecond.entrySet()) {
            Long key = entry.getKey();
            List<RunnerLocationReport> reports = entry.getValue();

            RunnerLocationReport reportBestLocation = reports.get(0);
            for (RunnerLocationReport report : reports) {
                if (reportBestLocation.getLocation().getSpeedAccuracyMetersPerSecond() > report.getLocation().getSpeedAccuracyMetersPerSecond()) {
                    reportBestLocation = report;
                }
            }

            bestReportPerSecond.put(key, reportBestLocation);
        }

        List<Long> keyList = new ArrayList<>(bestReportPerSecond.keySet());
        Collections.sort(keyList);

        List<RunnerLocationReport> output = new ArrayList<>();
        for (Long key : keyList) {
            output.add(bestReportPerSecond.get(key));
        }

        return output;
    }
}
