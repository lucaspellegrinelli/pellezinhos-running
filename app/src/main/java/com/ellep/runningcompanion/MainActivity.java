package com.ellep.runningcompanion;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;

import com.ellep.runningcompanion.databinding.ActivityMainBinding;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    private ActivityMainBinding binding;

    private long startTime = -1;

    private Handler handler;
    private Runnable updateRunnable;

    private TextToSpeech textToSpeech;
    private int ttsTime = 60;
    private boolean ttsEnabled = true;

    private boolean areListenersInstalled = false;

    private RunnerLocationManager runnerManager = new RunnerLocationManager(LOCATION_PERMISSION_REQUEST_CODE, Arrays.asList(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.FUSED_PROVIDER
    ));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Initializes Text to Speech
        textToSpeech = new TextToSpeech(getApplicationContext(), status -> {});
        textToSpeech.setLanguage(Locale.US);

        // Initialize the main loop
        handler = new Handler();
        updateRunnable = new Runnable() {
            @Override
            public void run() {
                updateUI();
                speakUpdates();
                handler.postDelayed(this, 1000);
            }
        };

        // Initialize the UI
        initializeButtonsUI();
        initializeTTSUI();
        updateHistory();

        areListenersInstalled = false;
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
            stopRun();
            binding.start.setEnabled(true);
            binding.stop.setEnabled(false);
        });
    }

    private void initializeTTSUI() {
        // Gets tts values from the ui
        ttsEnabled = binding.ttsEnabled.isChecked();
        ttsTime = (binding.ttsTime.getProgress() + 1) * 60;
        binding.ttsTimeView.setText(String.format("%d s", ttsTime));

        // Handles seek bar
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
        double locationAccuracy = runnerManager.getLastLocationAccuracy();
        String locationProvider = runnerManager.getLastLocationSource();

        binding.gpsStatus.setText(String.format("Acurácia do GPS: %.2f metros", locationAccuracy));
        binding.gpsSource.setText(String.format("Origem do GPS: %s", locationProvider));

        if (runStarted()) {
            double currentPacing = runnerManager.getCurrentSpeed();
            double overallPacing = runnerManager.getOverallSpeed(startTime);
            double distanceTraveled = runnerManager.getDistanceTraveled(startTime);
            long timeElapsed = runnerManager.getElapsedTime(startTime);

            overallPacing = Math.min(overallPacing, 50);
            currentPacing = Math.min(currentPacing, 50);

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
    protected void onResume() {
        super.onResume();

        if (!areListenersInstalled) {
            // Register location listeners
            runnerManager.registerLocationListeners(this, this);

            // Start the update Runnable
            handler.post(updateRunnable);

            areListenersInstalled = true;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Unregister location listener
//        runnerManager.unregisterLocationListeners(this);

        // Stop the update Runnable
//        handler.removeCallbacks(updateRunnable);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case LOCATION_PERMISSION_REQUEST_CODE: {
                // If the request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, do your location-related task
                    // you want to perform.
                } else {
                    // permission denied
                }

                return;
            }
        }
    }

    private void startRun() {
        startTime = Calendar.getInstance().getTimeInMillis();;
    }

    private void stopRun() {
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();

        try {
            JSONObject historyObj = new JSONObject(sharedPref.getString("history", "{\"history\": []}"));
            JSONArray history = historyObj.getJSONArray("history");

            JSONObject data = new JSONObject();
            data.put("when", startTime);
            data.put("time", runnerManager.getElapsedTime(startTime));
            data.put("distance", runnerManager.getDistanceTraveled(startTime));
            data.put("pace", runnerManager.getOverallSpeed(startTime));

            history.put(history.length(), data);
            historyObj.put("history", history);

            editor.putString("history", historyObj.toString());
            editor.apply();
        } catch(JSONException error) {
            System.out.println(error);
        } finally {
            updateHistory();
        }

        startTime = -1;
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