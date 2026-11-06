package org.sabbir.edutrace.data.models;

public class FocusTaskItem {
    private String id;
    private String title;
    private boolean isCompleted;

    public FocusTaskItem(String id, String title, boolean isCompleted) {
        this.id = id;
        this.title = title;
        this.isCompleted = isCompleted;
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public boolean isCompleted() { return isCompleted; }
    public void setCompleted(boolean completed) { isCompleted = completed; }
}

