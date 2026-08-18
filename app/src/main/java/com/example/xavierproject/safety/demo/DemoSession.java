package com.example.xavierproject.safety.demo;

import com.example.xavierproject.safety.model.UserRole;

public final class DemoSession {
    private static boolean active;
    private static UserRole role = UserRole.CITIZEN;

    private DemoSession() { }

    public static void start(UserRole demoRole) {
        active = true;
        role = demoRole;
        SafetyDemoStore.reset();
    }

    public static void stop() {
        active = false;
        role = UserRole.CITIZEN;
    }

    public static boolean isActive() { return active; }
    public static UserRole getRole() { return role; }
}
