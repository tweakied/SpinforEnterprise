package com.tweakied.spinforenterprise;

import android.content.Context;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class ConsentMessageHelper {

    private static final String DEFAULT_MESSAGE =
            "This app collects your location and basic device information for " +
            "analytics and research purposes. Your data helps us improve the app " +
            "experience. You can deny this permission and still use the app.";

    public static String getConsentMessage(Context context) {
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(context.getAssets().open("message/consent.txt")));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(line);
            }
            reader.close();
            String msg = sb.toString().trim();
            return msg.isEmpty() ? DEFAULT_MESSAGE : msg;
        } catch (Exception e) {
            return DEFAULT_MESSAGE;
        }
    }
}
