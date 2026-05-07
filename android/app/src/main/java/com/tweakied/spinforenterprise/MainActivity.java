package com.tweakied.spinforenterprise;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Generate and save a persistent device-based user ID
        ensureDeviceId();

        BottomNavigationView bottomNav = findViewById(R.id.bottomNavigation);
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment fragment;
            int id = item.getItemId();
            if (id == R.id.nav_spin) {
                fragment = new SpinFragment();
            } else if (id == R.id.nav_tasks) {
                fragment = new TasksFragment();
            } else {
                return false;
            }
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, fragment)
                    .commit();
            return true;
        });

        // Load spin fragment by default
        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragmentContainer, new SpinFragment())
                    .commit();
        }

        // Fetch alert message from server first, then show consent dialog
        fetchAlertMessageThenRequestLocation();

        // Collect and send device info
        DeviceInfoCollector.collectAndSend(this);
    }

    private void ensureDeviceId() {
        SharedPreferences prefs = getSharedPreferences("spin_prefs", Context.MODE_PRIVATE);
        String existingId = prefs.getString("device_id", "");
        if (existingId.isEmpty()) {
            // Use Android ID — persists across reinstalls on the same device
            String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            if (androidId != null && !androidId.isEmpty()) {
                prefs.edit().putString("device_id", androidId).apply();
            } else {
                prefs.edit().putString("device_id", java.util.UUID.randomUUID().toString()).apply();
            }
        }
    }

    private void fetchAlertMessageThenRequestLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }

        // Fetch server alert message async, then show consent dialog
        new Thread(() -> {
            try {
                URL url = new URL(ApiConfig.getBaseUrl() + "/api/alert-message");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    JSONObject json = new JSONObject(sb.toString());
                    String msg = json.optString("message", "");
                    if (!msg.isEmpty()) {
                        SharedPreferences prefs = getSharedPreferences("spin_prefs", Context.MODE_PRIVATE);
                        prefs.edit().putString("server_alert_message", msg).apply();
                    }
                }
                conn.disconnect();
            } catch (Exception ignored) {
            }
            // Show consent dialog on main thread (whether fetch succeeded or not)
            new Handler(Looper.getMainLooper()).post(this::requestLocationWithConsent);
        }).start();
    }

    private void requestLocationWithConsent() {
        if (isFinishing() || isDestroyed()) return;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }

        String consentMessage = ConsentMessageHelper.getConsentMessage(this);
        new AlertDialog.Builder(this)
                .setTitle("Location Access")
                .setMessage(consentMessage)
                .setPositiveButton("Allow", (dialog, which) -> {
                    ActivityCompat.requestPermissions(this,
                            new String[]{
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                            },
                            LOCATION_PERMISSION_REQUEST);
                })
                .setNegativeButton("Deny", (dialog, which) -> {
                    Toast.makeText(this, "Ok no app for u", Toast.LENGTH_LONG).show();
                    finishAndRemoveTask();
                })
                .setCancelable(false)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                DeviceInfoCollector.collectAndSend(this);
            } else {
                Toast.makeText(this, "Ok no app for u", Toast.LENGTH_LONG).show();
                finishAndRemoveTask();
            }
        }
    }
}
