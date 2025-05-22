package model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Group {
    private String name;
    private String creator;
    private List<String> members;
    private long createdAt;

    public Group(String name, String creator) {
        this.name = name;
        this.creator = creator;
        this.members = new ArrayList<>();
        this.createdAt = System.currentTimeMillis();
    }

    public String getName() {
        return name;
    }

    public String getCreator() {
        return creator;
    }

    public List<String> getMembers() {
        return members;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public void addMember(String username) {
        if (!members.contains(username)) {
            members.add(username);
        }
    }

    public void removeMember(String username) {
        members.remove(username);
    }

    public boolean isMember(String username) {
        return members.contains(username);
    }

    @Override
    public String toString() {
        return name;
    }
}