package org.sabbir.edutrace.desktop.data;

public class DesktopStudySession {
    private String id;
    private String subjectName;
    private long startTimestamp;
    private long endTimestamp;
    private long durationSeconds;
    private String mode; // e.g. "POMODORO" or "STOPWATCH"
    private String notes;

    public DesktopStudySession() {}

    public DesktopStudySession(String id, String subjectName, long startTimestamp, long endTimestamp, long durationSeconds, String mode, String notes) {
        this.id = id;
        this.subjectName = subjectName;
        this.startTimestamp = startTimestamp;
        this.endTimestamp = endTimestamp;
        this.durationSeconds = durationSeconds;
        this.mode = mode;
        this.notes = notes;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }

    public long getStartTimestamp() { return startTimestamp; }
    public void setStartTimestamp(long startTimestamp) { this.startTimestamp = startTimestamp; }
    public long getStartTime() { return startTimestamp; }

    public long getEndTimestamp() { return endTimestamp; }
    public void setEndTimestamp(long endTimestamp) { this.endTimestamp = endTimestamp; }

    public long getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(long durationSeconds) { this.durationSeconds = durationSeconds; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}

