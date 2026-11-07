package org.sabbir.edutrace.desktop.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class DesktopQuoteManager {

    private final List<String> englishQuotes = new ArrayList<>();
    private final List<String> bengaliQuotes = new ArrayList<>();
    private boolean isBengali = false;
    private int currentIndex = 0;
    private final Random random = new Random();

    public DesktopQuoteManager() {
        loadQuotes();
    }

    private void loadQuotes() {
        Gson gson = new Gson();

        // Load English Quotes
        try (InputStream is = getClass().getResourceAsStream("/quotes/quotes_en.json")) {
            if (is != null) {
                Map<String, List<String>> map = gson.fromJson(
                        new InputStreamReader(is, StandardCharsets.UTF_8),
                        new TypeToken<Map<String, List<String>>>() {}.getType()
                );
                if (map != null) {
                    if (map.containsKey("focus")) englishQuotes.addAll(map.get("focus"));
                    if (map.containsKey("flow")) englishQuotes.addAll(map.get("flow"));
                }
            }
        } catch (Exception ignored) {}

        // Load Bengali Quotes
        try (InputStream is = getClass().getResourceAsStream("/quotes/quotes_bn.json")) {
            if (is != null) {
                Map<String, List<String>> map = gson.fromJson(
                        new InputStreamReader(is, StandardCharsets.UTF_8),
                        new TypeToken<Map<String, List<String>>>() {}.getType()
                );
                if (map != null) {
                    if (map.containsKey("focus")) bengaliQuotes.addAll(map.get("focus"));
                    if (map.containsKey("flow")) bengaliQuotes.addAll(map.get("flow"));
                }
            }
        } catch (Exception ignored) {}

        // Fallbacks
        if (englishQuotes.isEmpty()) {
            englishQuotes.add("\"Starve your distractions, feed your focus.\" - Daniel Goleman");
            englishQuotes.add("\"Focus and simplicity... once you get there, you can move mountains.\" - Steve Jobs");
            englishQuotes.add("\"The successful warrior is the average man, with laser-like focus.\" - Bruce Lee");
        }
        if (bengaliQuotes.isEmpty()) {
            bengaliQuotes.add("\"বিক্ষিপ্ততাকে পরিহার করুন, মনোনিবেশকে লালন করুন।\" - ড্যানিয়েল গোলম্যান");
            bengaliQuotes.add("\"মনোযোগ এবং সরলতা—একবার আপনি সেখানে পৌঁছালে পাহাড় সরাতে পারবেন।\" - স্টিভ জবস");
        }
    }

    public boolean isBengali() {
        return isBengali;
    }

    public void setBengali(boolean bengali) {
        isBengali = bengali;
    }

    public String getCurrentQuote() {
        List<String> list = isBengali ? bengaliQuotes : englishQuotes;
        if (list.isEmpty()) return "Stay Focused.";
        if (currentIndex < 0 || currentIndex >= list.size()) {
            currentIndex = 0;
        }
        return list.get(currentIndex);
    }

    public String getNextQuote() {
        List<String> list = isBengali ? bengaliQuotes : englishQuotes;
        if (list.isEmpty()) return "Stay Focused.";
        currentIndex = (currentIndex + 1) % list.size();
        return list.get(currentIndex);
    }

    public String getRandomQuote() {
        List<String> list = isBengali ? bengaliQuotes : englishQuotes;
        if (list.isEmpty()) return "Stay Focused.";
        currentIndex = random.nextInt(list.size());
        return list.get(currentIndex);
    }
}
