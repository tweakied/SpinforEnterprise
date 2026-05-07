package com.tweakied.spinforenterprise;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class TasksFragment extends Fragment {

    private static final String API_BASE_URL = ApiConfig.getBaseUrl();
    private LinearLayout tasksContainer;
    private Handler handler = new Handler(Looper.getMainLooper());
    private List<CountDownTimer> timers = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tasks, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        tasksContainer = view.findViewById(R.id.tasksContainer);
        loadTasks();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        for (CountDownTimer timer : timers) {
            timer.cancel();
        }
        timers.clear();
    }

    private String getUserId() {
        if (getContext() == null) return "unknown";
        SharedPreferences prefs = getContext().getSharedPreferences("spin_prefs", Context.MODE_PRIVATE);
        String userId = prefs.getString("user_id", "");
        if (userId.isEmpty() || "unknown".equals(userId)) {
            // Fallback to device_id
            String deviceId = prefs.getString("device_id", "");
            if (!deviceId.isEmpty()) {
                return deviceId.length() > 12 ? deviceId.substring(0, 12) : deviceId;
            }
            return "unknown";
        }
        return userId;
    }

    private void loadTasks() {
        if (getContext() == null) return;
        new Thread(() -> {
            try {
                String userId = getUserId();
                URL url = new URL(API_BASE_URL + "/api/tasks/" + userId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                int code = conn.getResponseCode();
                if (code == 200) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();
                    JSONObject response = new JSONObject(sb.toString());
                    JSONArray tasks = response.getJSONArray("tasks");

                    handler.post(() -> {
                        if (getContext() != null) displayTasks(tasks);
                    });
                } else {
                    handler.post(() -> {
                        if (getContext() != null) showEmpty();
                    });
                }
                conn.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
                handler.post(() -> {
                    if (getContext() != null) showEmpty();
                });
            }
        }).start();
    }

    private void showEmpty() {
        if (tasksContainer == null) return;
        tasksContainer.removeAllViews();
        TextView empty = new TextView(getContext());
        empty.setText("No tasks available right now.");
        empty.setTextColor(0xFF888888);
        empty.setTextSize(16);
        empty.setPadding(0, 64, 0, 0);
        empty.setGravity(android.view.Gravity.CENTER);
        tasksContainer.addView(empty);
    }

    private void displayTasks(JSONArray tasks) {
        if (tasksContainer == null || getContext() == null) return;

        for (CountDownTimer timer : timers) {
            timer.cancel();
        }
        timers.clear();
        tasksContainer.removeAllViews();

        if (tasks.length() == 0) {
            showEmpty();
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(getContext());
        for (int i = 0; i < tasks.length(); i++) {
            try {
                JSONObject task = tasks.getJSONObject(i);
                View card = inflater.inflate(R.layout.item_task, tasksContainer, false);

                TextView titleView = card.findViewById(R.id.taskTitle);
                TextView descView = card.findViewById(R.id.taskDescription);
                TextView rewardView = card.findViewById(R.id.taskReward);
                ProgressBar progressBar = card.findViewById(R.id.taskProgress);
                TextView countdownView = card.findViewById(R.id.taskCountdown);
                Button btnSubmit = card.findViewById(R.id.btnSubmit);
                Button btnClaim = card.findViewById(R.id.btnClaim);

                String taskId = task.getString("id");
                String title = task.getString("title");
                String description = task.optString("description", "");
                int rewardSpins = task.optInt("reward_spins", 1);
                String status = task.optString("status", "pending");
                String expiresAt = task.optString("expires_at", null);

                titleView.setText(title);

                if (description != null && !description.isEmpty() && !description.equals("null")) {
                    descView.setText(description);
                    descView.setVisibility(View.VISIBLE);
                } else {
                    descView.setVisibility(View.GONE);
                }

                rewardView.setText("Reward: " + rewardSpins + " spin" + (rewardSpins > 1 ? "s" : ""));

                // Set progress and buttons based on status
                if ("approved".equals(status)) {
                    progressBar.setProgress(100);
                    btnClaim.setVisibility(View.VISIBLE);
                    btnSubmit.setVisibility(View.GONE);
                    btnClaim.setOnClickListener(v -> claimTask(taskId));
                } else if ("submitted".equals(status)) {
                    progressBar.setProgress(50);
                    btnSubmit.setVisibility(View.GONE);
                    btnClaim.setVisibility(View.GONE);
                    // Show waiting text
                    rewardView.setText("Reward: " + rewardSpins + " spin" + (rewardSpins > 1 ? "s" : "") + " — Waiting for approval");
                } else if ("denied".equals(status)) {
                    progressBar.setProgress(0);
                    btnSubmit.setVisibility(View.VISIBLE);
                    btnSubmit.setText("Resubmit");
                    btnClaim.setVisibility(View.GONE);
                    btnSubmit.setOnClickListener(v -> submitTask(taskId));
                } else {
                    // pending
                    progressBar.setProgress(0);
                    btnSubmit.setVisibility(View.VISIBLE);
                    btnClaim.setVisibility(View.GONE);
                    btnSubmit.setOnClickListener(v -> submitTask(taskId));
                }

                // Countdown timer
                if (expiresAt != null && !expiresAt.equals("null") && !expiresAt.isEmpty()) {
                    try {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
                        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                        String cleanExpiry = expiresAt;
                        if (cleanExpiry.contains("+")) {
                            cleanExpiry = cleanExpiry.substring(0, cleanExpiry.indexOf('+'));
                        }
                        if (cleanExpiry.contains(".")) {
                            cleanExpiry = cleanExpiry.substring(0, cleanExpiry.indexOf('.'));
                        }
                        Date expDate = sdf.parse(cleanExpiry);
                        if (expDate != null) {
                            long remaining = expDate.getTime() - System.currentTimeMillis();
                            if (remaining > 0) {
                                countdownView.setVisibility(View.VISIBLE);
                                CountDownTimer cdTimer = new CountDownTimer(remaining, 1000) {
                                    @Override
                                    public void onTick(long millisUntilFinished) {
                                        long hrs = millisUntilFinished / 3600000;
                                        long mins = (millisUntilFinished % 3600000) / 60000;
                                        long secs = (millisUntilFinished % 60000) / 1000;
                                        countdownView.setText(String.format(Locale.US,
                                                "Time left: %dh %dm %ds", hrs, mins, secs));
                                    }

                                    @Override
                                    public void onFinish() {
                                        tasksContainer.removeView(card);
                                    }
                                };
                                cdTimer.start();
                                timers.add(cdTimer);
                            } else {
                                // Already expired, don't show
                                continue;
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                tasksContainer.addView(card);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void submitTask(String taskId) {
        new Thread(() -> {
            try {
                String userId = getUserId();
                URL url = new URL(API_BASE_URL + "/api/tasks/submit/" + taskId + "/" + userId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                OutputStream os = conn.getOutputStream();
                os.write("{}".getBytes("UTF-8"));
                os.close();

                int code = conn.getResponseCode();
                conn.disconnect();

                handler.post(() -> {
                    if (getContext() == null) return;
                    if (code == 200) {
                        Toast.makeText(getContext(), "Task submitted! Waiting for approval.", Toast.LENGTH_SHORT).show();
                        loadTasks();
                    } else {
                        Toast.makeText(getContext(), "Failed to submit task.", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                handler.post(() -> {
                    if (getContext() != null)
                        Toast.makeText(getContext(), "Network error.", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void claimTask(String taskId) {
        new Thread(() -> {
            try {
                String userId = getUserId();
                URL url = new URL(API_BASE_URL + "/api/tasks/claim/" + taskId + "/" + userId);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                OutputStream os = conn.getOutputStream();
                os.write("{}".getBytes("UTF-8"));
                os.close();

                int code = conn.getResponseCode();
                if (code == 200) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();
                    JSONObject resp = new JSONObject(sb.toString());
                    int spins = resp.optInt("spins_awarded", 0);

                    handler.post(() -> {
                        if (getContext() == null) return;
                        Toast.makeText(getContext(),
                                "Claimed! +" + spins + " spin" + (spins > 1 ? "s" : ""),
                                Toast.LENGTH_SHORT).show();
                        loadTasks();
                    });
                }
                conn.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
                handler.post(() -> {
                    if (getContext() != null)
                        Toast.makeText(getContext(), "Network error.", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
}
