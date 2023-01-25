package com.ellep.runningcompanion;

import android.location.Location;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RunnerLocationManager {
    private final int CURRENT_SPEED_SECONDS = 15;
    private final int MS_PER_TIME_GROUP = 2500;

    private final double SIMILAR_ACCURACY_THRESHOLD = 0.5;
    private final int MS_WAIT_TIME_BEFORE_DISCONNECT = 10000;

    private List<RunnerLocationReport> locationReports = new ArrayList<>();

    public void addLocationReport(RunnerLocationReport report) {
        locationReports.add(report);
    }

    public double getCurrentSpeed() {
        long currentTime = Calendar.getInstance().getTimeInMillis();
        double rawSpeed = getSpeedInInterval(currentTime - CURRENT_SPEED_SECONDS * 1000);
        double timeSpeed = Utils.fracMinuteToTime(rawSpeed);
        return Math.min(timeSpeed, 50);
    }

    public double getOverallSpeed(long startTime) {
        double rawSpeed = getSpeedInInterval(startTime);
        double timeSpeed = Utils.fracMinuteToTime(rawSpeed);
        return Math.min(timeSpeed, 50);
    }

    public long getElapsedTime(long since) {
        long currentTime = Calendar.getInstance().getTimeInMillis();
        long timeDiffMs = currentTime - since;
        return timeDiffMs / 1000;
    }

    public boolean locationServiceConnected() {
        RunnerLocationReport lastLocationReport = getLastLocationReport();
        if (lastLocationReport == null) {
            return false;
        }

        double lastAccuracy = lastLocationReport.getLocation().getAccuracy();
        return lastAccuracy > 0 && lastAccuracy <= 5;
    }

    public double getSpeedInInterval(long since) {
        double distanceKm = getDistanceTraveled(since);
        long currentTime = Calendar.getInstance().getTimeInMillis();
        long timeDiffMs = currentTime - since;
        double timeDiffMin = timeDiffMs / 60000.0;
        return timeDiffMin / distanceKm;
    }

    public double getDistanceTraveled(long since) {
        List<RunnerLocationReport> reports = getLocalizationOptimizedSequence(since);
        return calculateDistanceOfReports(reports);
    }

    public double getAltitudeDistance(long since) {
        List<RunnerLocationReport> reports = getAltitudeOptimizedSequence(since);
        return calculateAltitudeOfReports(reports);
    }

    public double calculateAltitudeOfReports(List<RunnerLocationReport> reports) {
        double altitude = 0;
        Location lastLocation = null;
        for (RunnerLocationReport report : reports) {
            if (lastLocation != null) {
                altitude += Math.abs(lastLocation.getAltitude() - report.getLocation().getAltitude());
            }

            lastLocation = report.getLocation();
        }

        return altitude;
    }

    public double calculateDistanceOfReports(List<RunnerLocationReport> reports) {
        double distance = 0;
        Location lastLocation = null;
        for (RunnerLocationReport report : reports) {
            if (lastLocation != null) {
                distance += lastLocation.distanceTo(report.getLocation());
            }

            lastLocation = report.getLocation();
        }

        return distance / 1000.0;
    }

    public RunnerLocationReport getLastLocationReport() {
        long currentTime = Calendar.getInstance().getTimeInMillis();
        List<RunnerLocationReport> reports = getLocalizationOptimizedSequence(currentTime - MS_WAIT_TIME_BEFORE_DISCONNECT);
        if (reports.size() > 0) {
            Location currentLocation = reports.get(reports.size() - 1).getLocation();
            if (currentLocation != null)
                return reports.get(reports.size() - 1);
        }
        return null;
    }

    public RunnerLocationReport getLastAltitudeReport() {
        long currentTime = Calendar.getInstance().getTimeInMillis();
        List<RunnerLocationReport> reports = getAltitudeOptimizedSequence(currentTime - MS_WAIT_TIME_BEFORE_DISCONNECT);
        if (reports.size() > 0) {
            Location currentLocation = reports.get(reports.size() - 1).getLocation();
            if (currentLocation != null)
                return reports.get(reports.size() - 1);
        }
        return null;
    }

    public List<RunnerLocationReport> getLocalizationOptimizedSequence(long since) {
        return getOptimizedSequence(since, new RunnerSequenceOptimize() {
            @Override
            public boolean isReportValid(RunnerLocationReport report) {
                return report.getLocation().hasAccuracy() && report.getLocation().getAccuracy() <= 5;
            }

            @Override
            public double getReportWeight(RunnerLocationReport report) {
                return 1.0 / report.getLocation().getAccuracy();
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
            public double getReportWeight(RunnerLocationReport report) {
                return 1.0 / report.getLocation().getVerticalAccuracyMeters();
            }
        });
    }

    public List<RunnerLocationReport> getOptimizedSequence(long since, RunnerSequenceOptimize optimize) {
        HashMap<Long, List<RunnerLocationReport>> reportsPerTimeGroup = new HashMap<>();

        for (RunnerLocationReport report : locationReports) {
            if (optimize.isReportValid(report) && report.getTime() >= since) {
                long timeGroup = Math.round(report.getTime() / MS_PER_TIME_GROUP);
                if (reportsPerTimeGroup.containsKey(timeGroup)) {
                    reportsPerTimeGroup.get(timeGroup).add(report);
                } else {
                    List<RunnerLocationReport> listOfReports = new ArrayList<>();
                    listOfReports.add(report);
                    reportsPerTimeGroup.put(timeGroup, listOfReports);
                }
            }
        }

        HashMap<Long, RunnerLocationReport> reportPerTimeGroup = new HashMap<>();
        for (Map.Entry<Long, List<RunnerLocationReport>> entry : reportsPerTimeGroup.entrySet()) {
            Long key = entry.getKey();
            List<RunnerLocationReport> reports = entry.getValue();

            double averageTime = 0;
            double averageLat = 0;
            double averageLon = 0;
            double averageAltitude = 0;
            double averageSpeed = 0;
            float averageAccuracy = 0;
            float averageAltitudeAccuracy = 0;
            float averageSpeedAccuracy = 0;
            double weightSum = 0;
            for (RunnerLocationReport report : reports) {
                double reportWeight = optimize.getReportWeight(report);
                averageTime += report.getTime() * reportWeight;
                averageLat += report.getLocation().getLatitude() * reportWeight;
                averageLon += report.getLocation().getLongitude() * reportWeight;
                averageAltitude += report.getLocation().getAltitude() * reportWeight;
                averageSpeed = report.getLocation().getSpeed() * reportWeight;
                averageAccuracy += report.getLocation().getAccuracy() * reportWeight;
                averageAltitudeAccuracy += report.getLocation().getVerticalAccuracyMeters() * reportWeight;
                averageSpeedAccuracy += report.getLocation().getSpeedAccuracyMetersPerSecond() * reportWeight;
                weightSum += reportWeight;
            }

            averageTime /= weightSum;
            averageLat /= weightSum;
            averageLon /= weightSum;
            averageAltitude /= weightSum;
            averageSpeed /= weightSum;
            averageAccuracy /= weightSum;
            averageAltitudeAccuracy /= weightSum;
            averageSpeedAccuracy /= weightSum;

            Location averageLocation = reports.get(0).getLocation();
            averageLocation.reset();
            averageLocation.setLatitude(averageLat);
            averageLocation.setLongitude(averageLon);
            averageLocation.setAltitude(averageAltitude);
            averageLocation.setSpeed((float) averageSpeed);
            averageLocation.setAccuracy(averageAccuracy);
            averageLocation.setVerticalAccuracyMeters(averageAltitudeAccuracy);
            averageLocation.setSpeedAccuracyMetersPerSecond(averageSpeedAccuracy);

            RunnerLocationReport avgReport = new RunnerLocationReport("average", (long) averageTime, averageLocation);
            reportPerTimeGroup.put(key, avgReport);
        }

        List<Long> keyList = new ArrayList<>(reportPerTimeGroup.keySet());
        Collections.sort(keyList);

        List<RunnerLocationReport> output = new ArrayList<>();
        for (Long key : keyList) {
            output.add(reportPerTimeGroup.get(key));
        }

        return output;
    }
}
