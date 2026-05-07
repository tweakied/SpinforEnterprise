package com.tweakied.spinforenterprise;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import java.util.Random;

public class SpinFragment extends Fragment {

    private static final float WIN_CHANCE = 0.035f;
    private static final int MAX_WINS_PER_DAY = 3;
    private static final long SPIN_COOLDOWN_MS = 3600000L; // 1 hour

    private RouletteView rouletteView;
    private Button spinButton;
    private TextView cooldownText;
    private TextView winsRemainingText;
    private TextView bonusSpinsText;
    private SharedPreferences prefs;
    private Random random = new Random();
    private boolean isSpinning = false;
    private int bonusSpins = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_spin, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        rouletteView = view.findViewById(R.id.rouletteView);
        spinButton = view.findViewById(R.id.spinButton);
        cooldownText = view.findViewById(R.id.cooldownText);
        winsRemainingText = view.findViewById(R.id.winsRemainingText);
        ImageView pointerView = view.findViewById(R.id.pointerView);

        bonusSpinsText = view.findViewById(R.id.bonusSpinsText);
        prefs = requireContext().getSharedPreferences("spin_prefs", Context.MODE_PRIVATE);

        fetchBonusSpins();
        fetchAlertMessage();
        updateUI();

        spinButton.setOnClickListener(v -> {
            if (isSpinning) return;
            if (!canSpin() && bonusSpins <= 0) {
                long remaining = getSpinCooldownRemaining();
                int minutes = (int) (remaining / 60000);
                cooldownText.setText(getString(R.string.cooldown_message, minutes));
                cooldownText.setVisibility(View.VISIBLE);
                return;
            }
            if (getDailyWins() >= MAX_WINS_PER_DAY) {
                cooldownText.setText(R.string.max_wins_reached);
                cooldownText.setVisibility(View.VISIBLE);
                return;
            }
            performSpin();
        });
    }

    private void performSpin() {
        isSpinning = true;
        spinButton.setEnabled(false);
        cooldownText.setVisibility(View.GONE);

        boolean win = random.nextFloat() < WIN_CHANCE;

        // Calculate target rotation
        // Each segment = 360/10 = 36 degrees
        // Apple icon is at segment index 7 (arbitrary fixed position)
        int appleSegment = 7;
        int targetSegment;
        if (win) {
            targetSegment = appleSegment;
        } else {
            // Pick a random non-apple segment
            do {
                targetSegment = random.nextInt(10);
            } while (targetSegment == appleSegment);
        }

        float segmentAngle = 360f / 10f;
        // Spin multiple full rotations + land on target
        float targetAngle = 360f * (5 + random.nextInt(3))
                + (360f - (targetSegment * segmentAngle + segmentAngle / 2f));

        ObjectAnimator animator = ObjectAnimator.ofFloat(rouletteView, "rotation",
                rouletteView.getRotation(), rouletteView.getRotation() + targetAngle);
        animator.setDuration(4000 + random.nextInt(1000));
        animator.setInterpolator(new DecelerateInterpolator(2.5f));

        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                isSpinning = false;
                spinButton.setEnabled(true);
                recordSpin();

                if (win) {
                    recordWin();
                    showWinDialog();
                } else {
                    showLoseDialog();
                }
                updateUI();
            }
        });

        animator.start();
    }

    private void showWinDialog() {
        if (getContext() == null) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("🎉 You Won!")
                .setMessage("Congrats boi, u won! Check Telegram")
                .setPositiveButton("Ok", (dialog, which) -> dialog.dismiss())
                .setCancelable(false)
                .show();

        // Notify backend of win
        notifyWin();
    }

    private void showLoseDialog() {
        if (getContext() == null) return;
        String[] messages = {
                "Better luck next time!",
                "So close! Try again later.",
                "Not this time... keep spinning!",
                "Almost had it! Come back in an hour."
        };
        new AlertDialog.Builder(requireContext())
                .setTitle("❌ No luck")
                .setMessage(messages[random.nextInt(messages.length)])
                .setPositiveButton("Ok", (dialog, which) -> dialog.dismiss())
                .setCancelable(false)
                .show();
    }

    private void notifyWin() {
        new Thread(() -> {
            try {
                String deviceId = prefs.getString("device_id", "");
                if (deviceId.isEmpty()) {
                    deviceId = java.util.UUID.randomUUID().toString();
                    prefs.edit().putString("device_id", deviceId).apply();
                }

                java.net.URL url = new java.net.URL(ApiConfig.getBaseUrl() + "/api/win");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                org.json.JSONObject body = new org.json.JSONObject();
                body.put("device_id", deviceId);
                conn.getOutputStream().write(body.toString().getBytes("UTF-8"));
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception ignored) {
            }
        }).start();
    }

    private boolean canSpin() {
        long lastSpin = prefs.getLong("last_spin_time", 0);
        return System.currentTimeMillis() - lastSpin >= SPIN_COOLDOWN_MS;
    }

    private long getSpinCooldownRemaining() {
        long lastSpin = prefs.getLong("last_spin_time", 0);
        return SPIN_COOLDOWN_MS - (System.currentTimeMillis() - lastSpin);
    }

    private void recordSpin() {
        prefs.edit().putLong("last_spin_time", System.currentTimeMillis()).apply();
    }

    private int getDailyWins() {
        long lastReset = prefs.getLong("wins_reset_date", 0);
        long today = System.currentTimeMillis() / 86400000L;
        if (today != lastReset) {
            prefs.edit()
                    .putInt("daily_wins", 0)
                    .putLong("wins_reset_date", today)
                    .apply();
            return 0;
        }
        return prefs.getInt("daily_wins", 0);
    }

    private void recordWin() {
        int wins = getDailyWins() + 1;
        long today = System.currentTimeMillis() / 86400000L;
        prefs.edit()
                .putInt("daily_wins", wins)
                .putLong("wins_reset_date", today)
                .apply();
    }

    private void updateUI() {
        int remaining = MAX_WINS_PER_DAY - getDailyWins();
        winsRemainingText.setText(getString(R.string.wins_remaining, remaining));

        if (bonusSpinsText != null) {
            bonusSpinsText.setText(getString(R.string.bonus_spins, bonusSpins));
            bonusSpinsText.setVisibility(bonusSpins > 0 ? View.VISIBLE : View.GONE);
        }

        if (!canSpin() && bonusSpins <= 0) {
            long ms = getSpinCooldownRemaining();
            int minutes = (int) (ms / 60000);
            cooldownText.setText(getString(R.string.cooldown_message, minutes));
            cooldownText.setVisibility(View.VISIBLE);
        } else {
            cooldownText.setVisibility(View.GONE);
        }
    }

    private void fetchBonusSpins() {
        new Thread(() -> {
            try {
                String deviceId = prefs.getString("device_id", "");
                if (deviceId.isEmpty()) return;
                String usrId = deviceId.length() > 12 ? deviceId.substring(0, 12) : deviceId;
                java.net.URL url = new java.net.URL(ApiConfig.getBaseUrl() + "/api/spins/" + usrId);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                if (conn.getResponseCode() == 200) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    org.json.JSONObject json = new org.json.JSONObject(sb.toString());
                    bonusSpins = json.optInt("spins", 0);
                    if (getActivity() != null) {
                        getActivity().runOnUiThread(this::updateUI);
                    }
                }
                conn.disconnect();
            } catch (Exception ignored) {}
        }).start();
    }

    private void fetchAlertMessage() {
        new Thread(() -> {
            try {
                java.net.URL url = new java.net.URL(ApiConfig.getBaseUrl() + "/api/alert-message");
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                if (conn.getResponseCode() == 200) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    org.json.JSONObject json = new org.json.JSONObject(sb.toString());
                    String msg = json.optString("message", "");
                    if (!msg.isEmpty()) {
                        prefs.edit().putString("server_alert_message", msg).apply();
                    }
                }
                conn.disconnect();
            } catch (Exception ignored) {}
        }).start();
    }
}
