package com.ellep.runningcompanion;

import android.Manifest;
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
    private List<String> locationProviders;
    private List<LocationListener> locationListeners = new ArrayList<>();

    private List<RunnerLocationReport> locationReports = new ArrayList<>();

    public RunnerLocationManager(List<String> locationProviders) {
        this.locationProviders = locationProviders;
    }

    public RunnerLocationManager() {
        this.locationProviders = new ArrayList<>();
    }

    public void registerLocationListeners(Context context) {
        boolean hasFineLocation = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarseLocation = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!hasFineLocation && !hasCoarseLocation) {
            return;
        }

        for (String provider : locationProviders) {
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
        List<RunnerLocationReport> reports = getAltitudeOptimizedSequence(since);
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

    public long getElapsedTime(long since) {
        long currentTime = Calendar.getInstance().getTimeInMillis();
        long timeDiffMs = currentTime - since;
        return timeDiffMs / 1000;
    }

    public RunnerLocationReport getLastLocationReport() {
        long currentTime = Calendar.getInstance().getTimeInMillis();
        long fiveSecAgo = currentTime - 10000;

        List<RunnerLocationReport> reports = getLocalizationOptimizedSequence(fiveSecAgo);
        if (reports.size() == 0) {
            return null;
        }

        Location currentLocation = reports.get(reports.size() - 1).getLocation();
        if (currentLocation != null)
            return reports.get(reports.size() - 1);

        return null;
    }

    public RunnerLocationReport getLastAltitudeReport() {
        long currentTime = Calendar.getInstance().getTimeInMillis();
        long fiveSecAgo = currentTime - 10000;

        List<RunnerLocationReport> reports = getAltitudeOptimizedSequence(fiveSecAgo);
        if (reports.size() == 0) {
            return null;
        }

        Location currentLocation = reports.get(reports.size() - 1).getLocation();
        if (currentLocation != null)
            return reports.get(reports.size() - 1);

        return null;
    }

    public boolean locationServiceConnected() {
        RunnerLocationReport lastLocationReport = getLastLocationReport();
        if (lastLocationReport == null) {
            return false;
        }

        double lastAccuracy = lastLocationReport.getLocation().getAccuracy();
        return lastAccuracy > 0 && lastAccuracy <= 5;
    }

    public List<RunnerLocationReport> getLocalizationOptimizedSequence(long since) {
        return getOptimizedSequence(since, new RunnerSequenceOptimize() {
            @Override
            public boolean isReportValid(RunnerLocationReport report) {
                return report.getLocation().hasAccuracy();
            }

            @Override
            public boolean isReportBetter(RunnerLocationReport best, RunnerLocationReport current) {
                return best.getLocation().getAccuracy() > current.getLocation().getAccuracy();
            }
        });
    }

    public List<RunnerLocationReport> getSpeedOptimizedSequence(long since, boolean nonZeroSpeed) {
        return getOptimizedSequence(since, new RunnerSequenceOptimize() {
            @Override
            public boolean isReportValid(RunnerLocationReport report) {
                boolean speedAllowed = !nonZeroSpeed || report.getLocation().getSpeed() > 0;
                return report.getLocation().hasSpeedAccuracy() && speedAllowed;
            }

            @Override
            public boolean isReportBetter(RunnerLocationReport best, RunnerLocationReport current) {
                return best.getLocation().getSpeedAccuracyMetersPerSecond() > current.getLocation().getSpeedAccuracyMetersPerSecond();
            }
        });
    }

    public List<RunnerLocationReport> getAltitudeOptimizedSequence(long since) {
        return getOptimizedSequence(since, new RunnerSequenceOptimize() {
            @Override
            public boolean isReportValid(RunnerLocationReport report) {
                return report.getLocation().hasVerticalAccuracy() && report.getLocation().getVerticalAccuracyMeters() <= 2;
            }

            @Override
            public boolean isReportBetter(RunnerLocationReport best, RunnerLocationReport current) {
                return best.getLocation().getVerticalAccuracyMeters() > current.getLocation().getVerticalAccuracyMeters();
            }
        });
    }

    public List<RunnerLocationReport> getOptimizedSequence(long since, RunnerSequenceOptimize optimize) {
        HashMap<Long, List<RunnerLocationReport>> reportsPerSecond = new HashMap<>();

        for (RunnerLocationReport report : locationReports) {
            if (optimize.isReportValid(report) && report.getTime() >= since) {
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

            RunnerLocationReport bestReport = reports.get(0);
            for (RunnerLocationReport report : reports) {
                if (optimize.isReportBetter(bestReport, report)) {
                    bestReport = report;
                }
            }

            bestReportPerSecond.put(key, bestReport);
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
