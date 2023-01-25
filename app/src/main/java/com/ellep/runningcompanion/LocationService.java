package com.ellep.runningcompanion;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
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

import java.util.Arrays;

public class LocationService extends Service {
    private static final String CHANNEL_ID = "location_service_channel";
    private static final int NOTIFICATION_ID = 1;
    private static final String NOTIFICATION_TITLE = "Pellezinho's Running";

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;

    private final OtherLocationProviderManager otherLocationProviderManager = new OtherLocationProviderManager(Arrays.asList(
            LocationManager.GPS_PROVIDER,
            LocationManager.FUSED_PROVIDER
    ));

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

        // Handle new locations
        otherLocationProviderManager.registerLocationListeners(this, this::sendLocationBroadcast);
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult != null) {
                    sendLocationBroadcast(locationResult.getLastLocation(), "service");
                }
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

    private void sendLocationBroadcast(Location location, String source) {
        Intent intent = new Intent("location_update");
        intent.putExtra("location", location);
        intent.putExtra("source", source);
        sendBroadcast(intent);
    }

    private void createNotification() {
        Log.d("LocationService", "Creating notification");
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
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
                .setSmallIcon(R.drawable.ic_launcher_foreground)
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
        otherLocationProviderManager.unregisterLocationListeners(this);
        stopForeground(true);
        stopSelf();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
