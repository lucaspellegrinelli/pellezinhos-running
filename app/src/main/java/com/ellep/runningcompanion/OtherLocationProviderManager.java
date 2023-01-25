package com.ellep.runningcompanion;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.LocationListener;
import android.location.LocationManager;

import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;

public class OtherLocationProviderManager {
    private List<String> locationProviders;
    private List<LocationListener> locationListeners = new ArrayList<>();

    public OtherLocationProviderManager(List<String> locationProviders) {
        this.locationProviders = locationProviders;
    }

    public void registerLocationListeners(Context context, OtherLocationProviderCallback callback) {
        boolean hasFineLocation = ActivityCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        boolean hasCoarseLocation = ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!hasFineLocation && !hasCoarseLocation) {
            return;
        }

        for (String provider : locationProviders) {
            LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            LocationListener locationListener = location -> {
                callback.onLocationRecieved(location, provider);
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
}
