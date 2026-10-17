package com.safenavi.app.safety.model;

/** A located explanation, explicitly distinguishing an estimate from an official hazard. */
public final class RouteRiskPoint {
    private final String id, title, reason, source;
    private final double latitude, longitude, risk;
    private final boolean verified;
    public RouteRiskPoint(String id, double latitude, double longitude, String title,
                         String reason, double risk, boolean verified, String source) {
        this.id = id; this.latitude = latitude; this.longitude = longitude;
        this.title = title; this.reason = reason; this.risk = risk; this.verified = verified; this.source = source;
    }
    public String getId() { return id; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getTitle() { return title; }
    public String getReason() { return reason; }
    public double getRisk() { return risk; }
    public boolean isVerified() { return verified; }
    public String getSource() { return source; }
}
