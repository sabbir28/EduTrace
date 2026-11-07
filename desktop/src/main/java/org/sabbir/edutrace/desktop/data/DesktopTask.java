package org.sabbir.edutrace.desktop.data;

public class DesktopTask {
    private String id;
    private String title;
    private boolean completed;
    private long createdAt;

    public DesktopTask() {}

    public DesktopTask(String id, String title, boolean completed) {
        this.id = id;
        this.title = title;
        this.completed = completed;
        this.createdAt = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}

