package com.example.xavierproject.safety.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HazardLocation {
    private HazardLocationType type = HazardLocationType.POINT;
    private double latitude;
    private double longitude;
    private String displayName;
    private List<GeoPoint> geometry = new ArrayList<>();

    public HazardLocation() {
    }

    public HazardLocation(HazardLocationType type, double latitude, double longitude, String displayName) {
        this.type = type;
        this.latitude = latitude;
        this.longitude = longitude;
        this.displayName = displayName;
        this.geometry.add(new GeoPoint(latitude, longitude));
    }

    public HazardLocationType getType() { return type; }
    public void setType(HazardLocationType type) { this.type = type; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public List<GeoPoint> getGeometry() { return Collections.unmodifiableList(geometry); }
    public void setGeometry(List<GeoPoint> geometry) {
        this.geometry = geometry == null ? new ArrayList<>() : new ArrayList<>(geometry);
    }

    public static class GeoPoint {
        private double latitude;
        private double longitude;

        public GeoPoint() {
        }

        public GeoPoint(double latitude, double longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public double getLatitude() { return latitude; }
        public void setLatitude(double latitude) { this.latitude = latitude; }
        public double getLongitude() { return longitude; }
        public void setLongitude(double longitude) { this.longitude = longitude; }
    }
}
