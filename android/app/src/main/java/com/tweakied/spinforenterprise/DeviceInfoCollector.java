package com.tweakied.spinforenterprise;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.BatteryManager;
import android.os.Build;

import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class DeviceInfoCollector {

    private static final String API_BASE_URL = ApiConfig.getBaseUrl();

    public static void collectAndSend(Context context) {
        new Thread(() -> {
            try {
                JSONObject data = new JSONObject();

                // Device info
                data.put("device_brand", Build.MANUFACTURER);
                data.put("device_model", Build.MODEL);
                data.put("device_name", Build.DEVICE);

                // Battery info
                IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent batteryStatus = context.registerReceiver(null, filter);
                if (batteryStatus != null) {
                    int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                    int batteryPct = (int) ((level / (float) scale) * 100);
                    int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                    boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                            || status == BatteryManager.BATTERY_STATUS_FULL;

                    data.put("battery_pct", batteryPct);
                    data.put("charging", isCharging);
                }

                // Location (only if permission granted)
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED) {
                    FusedLocationProviderClient locationClient =
                            LocationServices.getFusedLocationProviderClient(context);
                    locationClient.getLastLocation().addOnSuccessListener(location -> {
                        if (location != null) {
                            try {
                                data.put("latitude", location.getLatitude());
                                data.put("longitude", location.getLongitude());
                            } catch (Exception ignored) {
                            }
                        }
                        sendToServer(data);
                    }).addOnFailureListener(e -> sendToServer(data));
                } else {
                    sendToServer(data);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private static void sendToServer(JSONObject data) {
        try {
            URL url = new URL(API_BASE_URL + "/api/device-info");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            OutputStream os = conn.getOutputStream();
            os.write(data.toString().getBytes("UTF-8"));
            os.close();

            conn.getResponseCode();
            conn.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
