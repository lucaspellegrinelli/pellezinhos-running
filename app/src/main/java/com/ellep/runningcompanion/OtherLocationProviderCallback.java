package com.ellep.runningcompanion;

import android.location.Location;

public interface OtherLocationProviderCallback {
    void onLocationRecieved(Location location, String provider);

}
