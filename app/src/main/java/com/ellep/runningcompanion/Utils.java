package com.ellep.runningcompanion;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

public class Utils {
    public static String formatTime(long seconds) {
        long hours = seconds / 3600;
        seconds -= hours * 3600;
        long minutes = seconds / 60;
        seconds -= minutes * 60;
        return String.format("%d:%02d:%02d", hours, minutes, seconds);
    }

    public static String formatDateTime(long unixTime) {
        LocalDateTime triggerTime =
                LocalDateTime.ofInstant(Instant.ofEpochMilli(unixTime),
                        TimeZone.getDefault().toZoneId());

        String pattern = "dd/MM HH:mm";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return triggerTime.format(formatter);
    }

    public static String formatDateTimeSeconds(long unixTime) {
        LocalDateTime triggerTime =
                LocalDateTime.ofInstant(Instant.ofEpochMilli(unixTime),
                        TimeZone.getDefault().toZoneId());

        String pattern = "HH:mm:ss";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
        return triggerTime.format(formatter);
    }
}
