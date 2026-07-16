package com.tip.user;

public enum UserRole {
    ADMIN,
    USER;

    public String springRole() {
        return "ROLE_" + name();
    }
}
