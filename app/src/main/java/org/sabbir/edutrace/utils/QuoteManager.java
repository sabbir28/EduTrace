package org.sabbir.edutrace.utils;
import org.sabbir.edutrace.R;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class QuoteManager {
    private List<String> focusQuotes = new ArrayList<>();
    private List<String> flowQuotes = new ArrayList<>();
    private Random random = new Random();
    private Context context;

    public QuoteManager(Context context) {
        this.context = context;
        loadQuotes();
        ensureDefaultQuotes();
    }

    private void loadQuotes() {
        if (context == null) {
            ensureDefaultQuotes();
            return;
        }

        String lang = "en";
        try {
            SharedPreferences prefs = context.getSharedPreferences("Settings", Context.MODE_PRIVATE);
            lang = prefs.getString("language", "en");
        } catch (Exception e) {
            e.printStackTrace();
        }

        String fileName = "bn".equalsIgnoreCase(lang) ? "quotes_bn.json" : "quotes_en.json";

        // 1. Check for updated quotes in OBB expansion pack
        String obbJson = ObbManager.loadStringFromObb(context, fileName);
        if (obbJson != null && !obbJson.trim().isEmpty()) {
            try {
                parseQuotesJson(obbJson);
                if (!focusQuotes.isEmpty()) return;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 2. Fall back to bundled application assets
        try (InputStream is = context.getAssets().open(fileName)) {
            int size = is.available();
            if (size > 0) {
                byte[] buffer = new byte[size];
                int read = is.read(buffer);
                if (read > 0) {
                    String json = new String(buffer, 0, read, StandardCharsets.UTF_8);
                    parseQuotesJson(json);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void parseQuotesJson(String json) {
        if (json == null || json.trim().isEmpty()) return;
        try {
            JSONObject obj = new JSONObject(json);

            JSONArray focusArr = obj.optJSONArray("focus");
            if (focusArr != null) {
                for (int i = 0; i < focusArr.length(); i++) {
                    String q = focusArr.optString(i, "").trim();
                    if (!q.isEmpty()) focusQuotes.add(q);
                }
            }

            JSONArray flowArr = obj.optJSONArray("flow");
            if (flowArr != null) {
                for (int i = 0; i < flowArr.length(); i++) {
                    String q = flowArr.optString(i, "").trim();
                    if (!q.isEmpty()) flowQuotes.add(q);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void ensureDefaultQuotes() {
        if (focusQuotes.isEmpty()) {
            focusQuotes.add("Focus on your goals, one step at a time.");
            focusQuotes.add("Keep going, you're doing great!");
            focusQuotes.add("Discipline equals freedom.");
            focusQuotes.add("Consistency is the mother of mastery.");
        }
        if (flowQuotes.isEmpty()) {
            flowQuotes.add("In the zone.");
            flowQuotes.add("Deep work in progress.");
            flowQuotes.add("Flow state achieved.");
        }
    }

    public String getRandomFocusQuote() {
        ensureDefaultQuotes();
        return focusQuotes.get(random.nextInt(focusQuotes.size()));
    }

    public String getFlowQuote() {
        ensureDefaultQuotes();
        return flowQuotes.get(random.nextInt(flowQuotes.size()));
    }
}
