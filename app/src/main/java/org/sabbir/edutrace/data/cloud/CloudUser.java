package org.sabbir.edutrace.data.cloud;

public class CloudUser {
    private int id;
    private String username;
    private String email;
    private String displayName;

    private String token;

    public CloudUser(int id, String username, String email, String displayName) {
        this(id, username, email, displayName, null);
    }

    public CloudUser(int id, String username, String email, String displayName, String token) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.displayName = displayName;
        this.token = token;
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

    public String getToken() {
        return token;
    }
}
