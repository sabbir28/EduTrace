package org.sabbir.edutrace.desktop.cloud;

public class DesktopCloudUser {
    private int id;
    private String username;
    private String email;
    private String displayName;
    private String token;

    public DesktopCloudUser() {}

    public DesktopCloudUser(int id, String username, String email, String displayName, String token) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.displayName = displayName;
        this.token = token;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDisplayName() {
        return (displayName != null && !displayName.trim().isEmpty()) ? displayName : username;
    }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
}
