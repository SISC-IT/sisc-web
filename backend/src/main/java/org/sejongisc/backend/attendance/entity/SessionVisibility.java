package org.sejongisc.backend.attendance.entity;

public enum SessionVisibility {
    PUBLIC("공개"),
    PRIVATE("비공개");

    private final String description;

    SessionVisibility(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
