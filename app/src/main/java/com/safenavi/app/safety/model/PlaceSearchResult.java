package com.safenavi.app.safety.model;

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
    public String getPrimaryText() {
        int comma = displayName.indexOf(',');
        return comma > 0 ? displayName.substring(0, comma).trim() : displayName;
    }
    public String getSecondaryText() {
        int comma = displayName.indexOf(',');
        return comma >= 0 ? displayName.substring(comma + 1).trim() : "Mumbai metropolitan region";
    }
}
