package com.ellep.runningcompanion;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.Granularity;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.util.Calendar;

public class LocationService extends Service {
    private static final String CHANNEL_ID = "location_service_channel";
    private static final int NOTIFICATION_ID = 1;
    private static final String NOTIFICATION_TITLE = "Pellezinho's Running";

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;

    public LocationService() {

    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Registers the channel
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Location Service",
                NotificationManager.IMPORTANCE_HIGH
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);

        // Creates the notification
        createNotification();

        // Create a new FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // Create a new LocationCallback
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    return;
                }

                long currentTime = Calendar.getInstance().getTimeInMillis();
                sendLocationBroadcast(locationResult.getLastLocation());

                String notificationMessage = buildNotificationMessage(locationResult.getLastLocation(), currentTime);
                updateNotification(notificationMessage);
            }
        };

        // Create a new LocationRequest
        locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                .setWaitForAccurateLocation(true)
                .setGranularity(Granularity.GRANULARITY_FINE)
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        boolean hasFineLocation = ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarseLocation = ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED;

        if (!hasFineLocation && !hasCoarseLocation) {
            return START_NOT_STICKY;
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
        return START_STICKY;
    }

    private void sendLocationBroadcast(Location location) {
        Intent intent = new Intent("location_update");
        intent.putExtra("location", location);
        sendBroadcast(intent);
    }

    private String buildNotificationMessage(Location location, long unixTime) {
        return String.format("[%s] Acc: %.2f m (⇅ %.1f m)", Utils.formatDateTimeSeconds(unixTime), location.getAccuracy(), location.getVerticalAccuracyMeters());
    }

    private void createNotification() {
        Log.d("LocationService", "Creating notification");
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setContentTitle(NOTIFICATION_TITLE)
                .setContentText("...")
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        startForeground(NOTIFICATION_ID, builder.build());
    }

    private void updateNotification(String content) {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setContentTitle(NOTIFICATION_TITLE)
                .setContentText(content)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_MIN);

        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, builder.build());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        fusedLocationClient.removeLocationUpdates(locationCallback);
        stopForeground(true);
        stopSelf();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
