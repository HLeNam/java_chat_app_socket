package model;

public class User {
    private String username;
    private String fullName;
    private String email;
    private long createdAt;

    public User(String username) {
        this.username = username;
    }

    public User(String username, String fullName, String email, long createdAt) {
        this.username = username;
        this.fullName = fullName;
        this.email = email;
        this.createdAt = createdAt;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return username;
    }
}
