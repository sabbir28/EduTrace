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
        public List<DesktopDegree> degrees = new ArrayList<>();
        public List<DesktopSubject> subjects = new ArrayList<>();
        public List<DesktopTask> tasks = new ArrayList<>();
        public List<DesktopStudySession> sessions = new ArrayList<>();
        public int pomodoroWorkMinutes = 25;
        public int pomodoroBreakMinutes = 5;
        public boolean isBengaliQuotes = false;
        public String lastSelectedSubject = "Data Structures";
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
        }

        // Seed default degrees and subjects if empty
        if (data.degrees == null || data.degrees.isEmpty()) {
            data.degrees = new ArrayList<>();
            data.degrees.add(new DesktopDegree(1, "Computer Science & Engineering", "#3B82F6"));
            data.degrees.add(new DesktopDegree(2, "Mathematics & Natural Sciences", "#8B5CF6"));
            data.degrees.add(new DesktopDegree(3, "General Focus & Self-Study", "#FFB800"));
        }

        if (data.subjects == null || data.subjects.isEmpty()) {
            data.subjects = new ArrayList<>();
            // CS Degree (id=1)
            data.subjects.add(new DesktopSubject(1, 1, "Data Structures", "#60A5FA"));
            data.subjects.add(new DesktopSubject(2, 1, "Algorithms & Problem Solving", "#F59E0B"));
            data.subjects.add(new DesktopSubject(3, 1, "Operating Systems", "#10B981"));
            data.subjects.add(new DesktopSubject(4, 1, "Database Systems", "#EC4899"));

            // Math Degree (id=2)
            data.subjects.add(new DesktopSubject(5, 2, "Linear Algebra", "#A78BFA"));
            data.subjects.add(new DesktopSubject(6, 2, "Calculus & Analysis", "#F43F5E"));
            data.subjects.add(new DesktopSubject(7, 2, "Classical Physics", "#F59E0B"));

            // General (id=3)
            data.subjects.add(new DesktopSubject(8, 3, "Deep Work Session", "#FFB800"));
            data.subjects.add(new DesktopSubject(9, 3, "Research & Reading", "#14B8A6"));
        }

        if (data.tasks == null) {
            data.tasks = new ArrayList<>();
        }

        if (data.sessions == null) {
            data.sessions = new ArrayList<>();
        }

        saveData();
    }

    public synchronized void saveData() {
        try (FileWriter writer = new FileWriter(dataFile, StandardCharsets.UTF_8)) {
            gson.toJson(data, writer);
        } catch (Exception ignored) {}
    }

    // ==========================================
    // Degrees Management
    // ==========================================

    public synchronized List<DesktopDegree> getDegrees() {
        return new ArrayList<>(data.degrees);
    }

    public synchronized DesktopDegree addDegree(String name, String colorHex) {
        if (name == null || name.trim().isEmpty()) return null;
        int nextId = 1;
        for (DesktopDegree d : data.degrees) {
            if (d.getId() >= nextId) nextId = d.getId() + 1;
        }
        DesktopDegree degree = new DesktopDegree(nextId, name.trim(), colorHex != null ? colorHex : "#FFB800");
        data.degrees.add(degree);
        saveData();
        return degree;
    }

    public synchronized void deleteDegree(int degreeId) {
        data.degrees.removeIf(d -> d.getId() == degreeId);
        // Cascade delete subjects for this degree
        data.subjects.removeIf(s -> s.getDegreeId() == degreeId);
        saveData();
    }

    public synchronized DesktopDegree getDegreeById(int degreeId) {
        for (DesktopDegree d : data.degrees) {
            if (d.getId() == degreeId) return d;
        }
        return null;
    }

    // ==========================================
    // Subjects Management
    // ==========================================

    public synchronized List<DesktopSubject> getSubjects() {
        return new ArrayList<>(data.subjects);
    }

    public synchronized List<DesktopSubject> getSubjectsForDegree(int degreeId) {
        List<DesktopSubject> result = new ArrayList<>();
        for (DesktopSubject s : data.subjects) {
            if (s.getDegreeId() == degreeId) {
                result.add(s);
            }
        }
        return result;
    }

    public synchronized DesktopSubject addSubject(int degreeId, String name, String colorHex) {
        if (name == null || name.trim().isEmpty()) return null;
        int nextId = 1;
        for (DesktopSubject s : data.subjects) {
            if (s.getId() >= nextId) nextId = s.getId() + 1;
        }
        DesktopSubject subject = new DesktopSubject(nextId, degreeId, name.trim(), colorHex != null ? colorHex : "#FACC15");
        data.subjects.add(subject);
        saveData();
        return subject;
    }

    public synchronized void deleteSubject(int subjectId) {
        data.subjects.removeIf(s -> s.getId() == subjectId);
        saveData();
    }

    public synchronized DesktopSubject getSubjectByName(String name) {
        if (name == null) return null;
        for (DesktopSubject s : data.subjects) {
            if (name.equalsIgnoreCase(s.getName())) return s;
        }
        return null;
    }

    // ==========================================
    // Tasks & Sessions Management
    // ==========================================

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
            if (s.getStartTimestamp() >= midnight) {
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
            if (s.getStartTimestamp() >= midnight && subjectName.equalsIgnoreCase(s.getSubjectName())) {
                total += s.getDurationSeconds();
            }
        }
        return total;
    }

    public synchronized long getTotalSubjectStudySeconds(String subjectName) {
        if (subjectName == null) return 0;
        long total = 0;
        for (DesktopStudySession s : data.sessions) {
            if (subjectName.equalsIgnoreCase(s.getSubjectName())) {
                total += s.getDurationSeconds();
            }
        }
        return total;
    }

    public synchronized long getTotalDegreeStudySeconds(int degreeId) {
        List<DesktopSubject> subs = getSubjectsForDegree(degreeId);
        long total = 0;
        for (DesktopSubject sub : subs) {
            total += getTotalSubjectStudySeconds(sub.getName());
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
