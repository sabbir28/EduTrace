package org.sabbir.edutrace.utils;

import android.content.pm.ApplicationInfo;
import android.os.Build;

import org.sabbir.edutrace.data.models.FocusAppItem;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Intelligent App Categorization Engine for EduTrace Focus Launcher.
 * Classifies installed applications into specialized categories (Games, AI & Study, Social,
 * Essentials, Productivity) using Android OS ApplicationInfo metadata, package signatures,
 * and comprehensive heuristic dictionaries.
 */
public class AppCategoryManager {

    private static final Set<String> GAME_KEYWORDS = new HashSet<>(Arrays.asList(
            "game", "games", "play", "pubg", "freefire", "roblox", "minecraft", "supercell",
            "clash", "brawl", "chess", "genshin", "subway", "temple", "asphalt", "candy",
            "crush", "ludo", "amongus", "pokemon", "fifa", "ea.", "gameloft", "king.",
            "epicgames", "riot", "blizzard", "steam", "playstation", "xbox", "nintendo",
            "sniper", "racing", "arcade", "rpg", "puzzle", "action", "casino", "shooter",
            "codm", "shadowfight", "angrybirds", "ninja", "car", "kart", "sonic", "mario"
    ));

    private static final Set<String> AI_STUDY_KEYWORDS = new HashSet<>(Arrays.asList(
            "openai", "chatgpt", "anthropic", "claude", "gemini", "bard", "copilot", "perplexity",
            "poe", "deepseek", "huggingface", "midjourney", "edutrace", "duolingo", "khan",
            "coursera", "udemy", "edx", "quizlet", "anki", "brainly", "notion", "obsidian",
            "evernote", "forest", "pomodoro", "study", "learn", "math", "physics", "chem",
            "dictionary", "books", "kindle", "audible", "reader", "scholar", "byju", "sololearn"
    ));

    private static final Set<String> SOCIAL_KEYWORDS = new HashSet<>(Arrays.asList(
            "facebook", "instagram", "tiktok", "snapchat", "twitter", "x.corp", "reddit",
            "pinterest", "linkedin", "tumblr", "threads", "discord", "youtube", "netflix",
            "spotify", "twitch", "vimeo", "primevideo", "disney", "hotstar", "tinder",
            "bumble", "hiki", "streaming", "9gag", "reels", "shorts", "wechat", "line"
    ));

    private static final Set<String> ESSENTIALS_KEYWORDS = new HashSet<>(Arrays.asList(
            "dialer", "telecom", "phone", "mms", "messaging", "message", "whatsapp", "telegram",
            "signal", "gmail", "mail", "maps", "deskclock", "clock", "calendar", "contacts"
    ));

    /**
     * Categorizes a FocusAppItem using Android ApplicationInfo flags and heuristic signatures.
     */
    public static void categorizeApp(FocusAppItem item, ApplicationInfo appInfo) {
        if (item == null) return;

        String pkg = (item.getPackageName() != null ? item.getPackageName() : "").toLowerCase(Locale.US);
        String label = (item.getAppLabel() != null ? item.getAppLabel() : "").toLowerCase(Locale.US);

        // 1. Android OS Game Flag & Category (API 26+)
        boolean isOsGame = false;
        if (appInfo != null) {
            if ((appInfo.flags & ApplicationInfo.FLAG_IS_GAME) != 0) {
                isOsGame = true;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (appInfo.category == ApplicationInfo.CATEGORY_GAME) {
                    isOsGame = true;
                }
            }
        }

        // 2. Game Heuristics
        if (isOsGame || matchesKeywords(pkg, label, GAME_KEYWORDS)) {
            item.setGame(true);
            item.setCategory(FocusAppItem.CAT_GAMES);
            item.setDailyEssential(false);
            return;
        }

        // 3. AI & Study Tools
        if (matchesKeywords(pkg, label, AI_STUDY_KEYWORDS) || pkg.contains("edutrace")) {
            item.setAiTool(true);
            item.setCategory(FocusAppItem.CAT_AI_STUDY);
            item.setCurated(true);
            return;
        }

        // 4. Daily Essentials & Communication
        if (matchesKeywords(pkg, label, ESSENTIALS_KEYWORDS)) {
            item.setDailyEssential(true);
            item.setCategory(FocusAppItem.CAT_ESSENTIALS);
            return;
        }

        // 5. Social & Entertainment
        if (matchesKeywords(pkg, label, SOCIAL_KEYWORDS)) {
            item.setCategory(FocusAppItem.CAT_SOCIAL);
            item.setDailyEssential(false);
            return;
        }

        // 6. Default to Productivity / Utilities
        item.setCategory(FocusAppItem.CAT_PRODUCTIVITY);
    }

    private static boolean matchesKeywords(String pkg, String label, Set<String> keywords) {
        for (String kw : keywords) {
            if (pkg.contains(kw) || label.contains(kw)) {
                return true;
            }
        }
        return false;
    }
}
