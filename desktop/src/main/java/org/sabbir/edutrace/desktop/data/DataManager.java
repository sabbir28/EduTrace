package org.sabbir.edutrace.desktop.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.sabbir.edutrace.desktop.cloud.DesktopCloudUser;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class DataManager {

    private static DataManager instance;

    public static synchronized DataManager getInstance() {
        if (instance == null) {
            instance = new DataManager();
        }
        return instance;
    }

    public static class StorageData {
        public List<DesktopTask> tasks = new ArrayList<>();
        public List<DesktopStudySession> sessions = new ArrayList<>();
        public int pomodoroWorkMinutes = 25;
        public int pomodoroBreakMinutes = 5;
        public boolean isBengaliQuotes = false;
        public String lastSelectedSubject = "Mathematics";
        public DesktopCloudUser currentUser = null;
        public String serverUrl = "https://edutrace-theta.vercel.app/api";
    }

    private final File dataFile;
    private final Gson gson;
    private StorageData data;

    private DataManager() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        String userHome = System.getProperty("user.home", ".");
        File dir = new File(userHome, ".edutrace");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        this.dataFile = new File(dir, "desktop_data.json");
        loadData();
    }

    public synchronized void loadData() {
        if (dataFile.exists()) {
            try (FileReader reader = new FileReader(dataFile, StandardCharsets.UTF_8)) {
                data = gson.fromJson(reader, StorageData.class);
            } catch (Exception e) {
                data = new StorageData();
            }
        }
        if (data == null) {
            data = new StorageData();
            // Seed default focus tasks
            data.tasks.add(new DesktopTask("1", "Review course lecture notes", false));
            data.tasks.add(new DesktopTask("2", "Solve 5 practice problems", false));
            data.tasks.add(new DesktopTask("3", "Complete 25-minute deep focus session", false));
            saveData();
        }
    }

    public synchronized void saveData() {
        try (FileWriter writer = new FileWriter(dataFile, StandardCharsets.UTF_8)) {
            gson.toJson(data, writer);
        } catch (Exception ignored) {}
    }

    public synchronized List<DesktopTask> getTasks() {
        return new ArrayList<>(data.tasks);
    }

    public synchronized void addTask(String title) {
        if (title == null || title.trim().isEmpty()) return;
        DesktopTask task = new DesktopTask(String.valueOf(System.currentTimeMillis()), title.trim(), false);
        data.tasks.add(task);
        saveData();
    }

    public synchronized void toggleTask(String id) {
        for (DesktopTask t : data.tasks) {
            if (t.getId().equals(id)) {
                t.setCompleted(!t.isCompleted());
                break;
            }
        }
        saveData();
    }

    public synchronized void deleteTask(String id) {
        data.tasks.removeIf(t -> t.getId().equals(id));
        saveData();
    }

    public synchronized List<DesktopStudySession> getSessions() {
        return new ArrayList<>(data.sessions);
    }

    public synchronized void addSession(DesktopStudySession session) {
        if (session != null) {
            data.sessions.add(session);
            saveData();
        }
    }

    public synchronized long getTodayTotalStudySeconds() {
        long midnight = getTodayMidnightMillis();
        long total = 0;
        for (DesktopStudySession s : data.sessions) {
            if (s.getStartTime() >= midnight) {
                total += s.getDurationSeconds();
            }
        }
        return total;
    }

    public synchronized long getTodaySubjectStudySeconds(String subjectName) {
        if (subjectName == null) return 0;
        long midnight = getTodayMidnightMillis();
        long total = 0;
        for (DesktopStudySession s : data.sessions) {
            if (s.getStartTime() >= midnight && subjectName.equalsIgnoreCase(s.getSubjectName())) {
                total += s.getDurationSeconds();
            }
        }
        return total;
    }

    private long getTodayMidnightMillis() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    public synchronized void clearAllData() {
        data.tasks.clear();
        data.sessions.clear();
        saveData();
    }

    public StorageData getData() {
        return data;
    }

    public File getDataFile() {
        return dataFile;
    }
}

