package com.example.xavierproject.safety.model;

public class PlaceSearchResult {
    private final String displayName;
    private final double latitude;
    private final double longitude;

    public PlaceSearchResult(String displayName, double latitude, double longitude) {
        this.displayName = displayName;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getDisplayName() { return displayName; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
}
