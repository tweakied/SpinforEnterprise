package com.tweakied.spinforenterprise;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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

        // Request location with consent dialog
        requestLocationWithConsent();

        // Collect and send device info
        DeviceInfoCollector.collectAndSend(this);
    }

    private void requestLocationWithConsent() {
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
                    Toast.makeText(this, "Location permission is required. App will close.", Toast.LENGTH_LONG).show();
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
                Toast.makeText(this, "Location permission denied. App will close.", Toast.LENGTH_LONG).show();
                finishAndRemoveTask();
            }
        }
    }
}
