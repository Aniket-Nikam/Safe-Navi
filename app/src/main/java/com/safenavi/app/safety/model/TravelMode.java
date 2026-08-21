package com.safenavi.app.safety.model;

public enum TravelMode {
    DRIVING("auto", "Drive"), WALKING("pedestrian", "Walk"), CYCLING("bicycle", "Cycle");

    private final String providerProfile;
    private final String label;

    TravelMode(String providerProfile, String label) {
        this.providerProfile = providerProfile;
        this.label = label;
    }

    public String getProviderProfile() { return providerProfile; }
    public String getLabel() { return label; }
}
