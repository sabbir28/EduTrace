package org.sabbir.edutrace.data.cloud;

public class CloudUser {
    private int id;
    private String username;
    private String email;
    private String displayName;

    public CloudUser(int id, String username, String email, String displayName) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.displayName = displayName;
    }

    public int getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return (displayName != null && !displayName.trim().isEmpty()) ? displayName : username;
    }
}
