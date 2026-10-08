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

        // 1. Check for updated quotes in OBB expansion pack
        String obbJson = ObbManager.loadStringFromObb(context, fileName);
        if (obbJson != null && !obbJson.trim().isEmpty()) {
            try {
                parseQuotesJson(obbJson);
                return;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // 2. Fall back to bundled application assets
        try {
            InputStream is = context.getAssets().open(fileName);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            String json = new String(buffer, StandardCharsets.UTF_8);
            parseQuotesJson(json);
        } catch (Exception e) {
            e.printStackTrace();
            // Fallback to minimal hardcoded quotes if JSON fails
            focusQuotes.add("Keep going, you're doing great!");
            flowQuotes.add("Flow state achieved.");
        }
    }

    private void parseQuotesJson(String json) throws Exception {
        JSONObject obj = new JSONObject(json);

        JSONArray focusArr = obj.optJSONArray("focus");
        if (focusArr != null) {
            for (int i = 0; i < focusArr.length(); i++) {
                focusQuotes.add(focusArr.getString(i));
            }
        }

        JSONArray flowArr = obj.optJSONArray("flow");
        if (flowArr != null) {
            for (int i = 0; i < flowArr.length(); i++) {
                flowQuotes.add(flowArr.getString(i));
            }
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
