package com.ellep.runningcompanion;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.SeekBar;

import com.ellep.runningcompanion.databinding.ActivityMainBinding;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    private final int UI_UPDATE_TIME_MS = 1000;
    private final double GPS_MIN_ACCURACY = 5.0;

    private ActivityMainBinding binding;

    private long startTime = -1;

    private Handler handler;
    private Runnable updateRunnable;

    private TextToSpeech textToSpeech;
    private int ttsTime = 60;
    private boolean ttsEnabled = true;

    private boolean servicesRegistered = false;

    private final RunnerLocationManager runnerManager = new RunnerLocationManager();

    private BroadcastReceiver locationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Location location = intent.getParcelableExtra("location");
            String source = intent.getStringExtra("source");
            long currentTime = Calendar.getInstance().getTimeInMillis();

            if (location.hasAccuracy() && location.getAccuracy() <= GPS_MIN_ACCURACY) {
                runnerManager.addLocationReport(new RunnerLocationReport(source, currentTime, location));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Inflates the layout
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initialize the UI
        initializeButtonsUI();
        initializeTTSUI();
        initializeConfigUI();
        updateHistory();

        // Asks the user for permissions
        checkPermissions();

        // Initializes Text to Speech
        textToSpeech = new TextToSpeech(getApplicationContext(), status -> {});
        textToSpeech.setLanguage(Locale.US);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if(checkPermissions()) {
            setupServices();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (!runStarted()) {
            destroyServices();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        destroyServices();
    }

    private boolean checkPermissions() {
        boolean needsFineLocation = ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED;
        boolean needsCoarseLocation = ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED;

        if (needsFineLocation || needsCoarseLocation) {
            // If the permission is not granted, request it from the user
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOCATION_PERMISSION_REQUEST_CODE
            );

            return false;
        }

        return true;
    }

    private void setupServices() {
        // Initialize the main loop
        handler = new Handler();
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateUI();
                speakUpdates();
                handler.postDelayed(this, UI_UPDATE_TIME_MS);
            }
        };
        handler.post(updateRunnable);

        // Starts location service
        Intent intent = new Intent(this, LocationService.class);
        startForegroundService(intent);

        // Register location service
        IntentFilter filter = new IntentFilter("location_update");
        registerReceiver(locationReceiver, filter);

        servicesRegistered = true;
    }

    private void destroyServices() {
        if (!servicesRegistered)
            return;

        stopService(new Intent(this, LocationService.class));
        handler.removeCallbacks(updateRunnable);

        try {
            unregisterReceiver(locationReceiver);
        } catch(RuntimeException error) {
            Log.d("MainActivity", error.getMessage());
        }
    }

    private void initializeButtonsUI() {
        // Controls the button enabled states
        binding.start.setEnabled(false);
        binding.stop.setEnabled(false);

        binding.start.setOnClickListener(v -> {
            startRun();
            updateUI();
            binding.start.setEnabled(false);
            binding.stop.setEnabled(true);
        });

        binding.stop.setOnClickListener(v -> {
            confirmStop();
        });
    }

    private void initializeConfigUI() {
        // Handles seek bar
        runnerManager.setCurrentSpeedTimeBuffer((binding.currentSpeedBuffer.getProgress() + 2) * 5);
        binding.currentSpeedBufferView.setText(String.format("%d s", runnerManager.getCurrentSpeedTimeBuffer()));
        binding.currentSpeedBuffer.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                runnerManager.setCurrentSpeedTimeBuffer((progress + 2) * 5);
                binding.currentSpeedBufferView.setText(String.format("%d s", runnerManager.getCurrentSpeedTimeBuffer()));
            }
        });

        runnerManager.setUseWeightSquared(binding.squaredWeightEnabled.isChecked());
        binding.squaredWeightEnabled.setChecked(runnerManager.isUseWeightSquared());
        binding.squaredWeightEnabled.setOnCheckedChangeListener((compoundButton, isChecked) -> {
            runnerManager.setUseWeightSquared(isChecked);
            updateUI();
        });
    }

    private void confirmStop() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setMessage("Tem certeza que deseja parar?");

        // Add the buttons
        builder.setPositiveButton("Sim", (dialog, id) -> {
            // User clicked OK button
            stopRun();
            dialog.dismiss();
            binding.start.setEnabled(true);
            binding.stop.setEnabled(false);
        });

        builder.setNegativeButton("Cancelar", (dialog, id) -> {
            // User cancelled the dialog
            dialog.cancel();
        });

        // Create the AlertDialog
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void initializeTTSUI() {
        // Handles seek bar
        ttsTime = (binding.ttsTime.getProgress() + 1) * 60;
        binding.ttsTimeView.setText(String.format("%d s", ttsTime));
        binding.ttsTime.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                ttsTime = (progress + 1) * 60;
                binding.ttsTimeView.setText(String.format("%d s", ttsTime));
            }
        });

        // Handles checkbox
        ttsEnabled = binding.ttsEnabled.isChecked();
        binding.ttsEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
            ttsEnabled = isChecked;
        });
    }

    private void speakUpdates() {
        long timeElapsed = runnerManager.getElapsedTime(startTime);
        if (ttsEnabled && runStarted() && timeElapsed > 0 && timeElapsed % ttsTime == 0) {
            double currentPacing = runnerManager.getCurrentSpeed();
            double overallPacing = runnerManager.getOverallSpeed(startTime);
            double distanceTraveled = runnerManager.getDistanceTraveled(startTime);

            String currPaceText = "Rítmo atual é" + numberToTTS(currentPacing);
            String overallPaceText = "Rítmo global é " + numberToTTS(overallPacing);
            String distanceText = String.format("Distância percorrida é %.1f quilômetros", distanceTraveled);
            String fullText = currPaceText + ". " + overallPaceText + ". " + distanceText;

            textToSpeech.speak(fullText, TextToSpeech.QUEUE_FLUSH, null, "");
        }
    }

    private void updateUI() {
        RunnerLocationReport lastLocation = runnerManager.getLastLocationReport();
        RunnerLocationReport lastAltitude = runnerManager.getLastAltitudeReport();

        double locationAccuracy = lastLocation != null ? lastLocation.getLocation().getAccuracy() : 0;
//        String locationProvider = lastLocation != null ? lastLocation.getSource() : "unknown";
        double altitudeAccuracy = lastAltitude != null ? lastAltitude.getLocation().getVerticalAccuracyMeters() : 0;
//        String altitudeProvider = lastAltitude != null ? lastAltitude.getSource() : "unknown";

        binding.gpsStatus.setText(String.format("Acurácia do GPS: %.2f m (⇅ %.2f m)", locationAccuracy, altitudeAccuracy));

        if (runStarted()) {
            double currentPacing = runnerManager.getCurrentSpeed();
            double overallPacing = runnerManager.getOverallSpeed(startTime);
            double distanceTraveled = runnerManager.getDistanceTraveled(startTime);
            long timeElapsed = runnerManager.getElapsedTime(startTime);

            binding.distance.setText(String.format("%.2f km", distanceTraveled));
            binding.time.setText(Utils.formatTime(timeElapsed));
            binding.pacing.setText(String.format("%.2f min/km", overallPacing));
            binding.currentPacing.setText(String.format("%.2f min/km", currentPacing));
        }

        if (!binding.start.isEnabled() && !runStarted()) {
            binding.start.setEnabled(runnerManager.locationServiceConnected());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupServices();
            }
        }
    }

    private void startRun() {
        startTime = Calendar.getInstance().getTimeInMillis();
    }

    private void stopRun() {
        long tempStartTime = startTime;
        startTime = -1;
        storeHistory(tempStartTime);
    }

    private void storeHistory(long fromTime) {
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();

        try {
            JSONObject historyObj = new JSONObject(sharedPref.getString("history", "{\"history\": []}"));
            JSONArray history = historyObj.getJSONArray("history");

            JSONObject data = new JSONObject();
            data.put("when", fromTime);
            data.put("time", runnerManager.getElapsedTime(fromTime));
            data.put("distance", runnerManager.getDistanceTraveled(fromTime));
            data.put("pace", runnerManager.getOverallSpeed(fromTime));
            data.put("altimetry", runnerManager.getAltitudeDistance(fromTime));

            history.put(history.length(), data);
            historyObj.put("history", history);

            editor.putString("history", historyObj.toString());
            editor.apply();
        } catch(JSONException error) {
            System.out.println(error);
        } finally {
            updateHistory();
        }
    }

    private void updateHistory() {
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);

        try {
            JSONObject historyObj = new JSONObject(sharedPref.getString("history", "{\"history\": []}"));
            JSONArray history = historyObj.getJSONArray("history");

            List<HistoryItem> items = new ArrayList<>();
            for (int i = 0; i < history.length(); i++) {
                items.add(new HistoryItem(history.getJSONObject(i)));
            }

            items.sort((historyItem, t1) -> (int) (t1.getWhen() - historyItem.getWhen()));

            binding.historyList.setAdapter(new HistoryAdapter(this, items));
        } catch(JSONException error) {
            System.out.println(error);
        }
    }

    private boolean runStarted() {
        return startTime >= 0;
    }

    private String numberToTTS(double number) {
        int intPart = (int)number;
        int fracPart = 100 * (int)(number - (double)intPart);
        return String.format("%d e %d", intPart, fracPart);
    }
}