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
    }

    private void loadQuotes() {
        SharedPreferences prefs = context.getSharedPreferences("Settings", Context.MODE_PRIVATE);
        String lang = prefs.getString("language", "en");
        String fileName = lang.equals("bn") ? "quotes_bn.json" : "quotes_en.json";

        try {
            InputStream is = context.getAssets().open(fileName);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String json = new String(buffer, StandardCharsets.UTF_8);
            JSONObject obj = new JSONObject(json);

            JSONArray focusArr = obj.getJSONArray("focus");
            for (int i = 0; i < focusArr.length(); i++) {
                focusQuotes.add(focusArr.getString(i));
            }

            JSONArray flowArr = obj.getJSONArray("flow");
            for (int i = 0; i < flowArr.length(); i++) {
                flowQuotes.add(flowArr.getString(i));
            }
        } catch (Exception e) {
            e.printStackTrace();
            // Fallback to minimal hardcoded quotes if JSON fails
            focusQuotes.add("Keep going, you're doing great!");
            flowQuotes.add("Flow state achieved.");
        }
    }

    public String getRandomFocusQuote() {
        if (focusQuotes.isEmpty()) return "Focus on your goals.";
        return focusQuotes.get(random.nextInt(focusQuotes.size()));
    }

    public String getFlowQuote() {
        if (flowQuotes.isEmpty()) return "In the zone.";
        return flowQuotes.get(random.nextInt(flowQuotes.size()));
    }
}
