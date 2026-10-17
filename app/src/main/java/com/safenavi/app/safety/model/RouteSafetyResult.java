package com.safenavi.app.safety.model;

public class RouteSafetyResult {
    private final RouteCandidate route;
    private final RouteProfile profile;
    private final double riskExposure;
    private final double score;
    private final java.util.List<RouteRiskPoint> riskPoints;

    public RouteSafetyResult(RouteCandidate route, RouteProfile profile, double riskExposure, double score) {
        this(route, profile, riskExposure, score, java.util.Collections.emptyList());
    }

    public RouteSafetyResult(RouteCandidate route, RouteProfile profile, double riskExposure, double score,
                             java.util.List<RouteRiskPoint> riskPoints) {
        this.route = route;
        this.profile = profile;
        this.riskExposure = riskExposure;
        this.score = score;
        this.riskPoints = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(riskPoints));
    }

    public RouteCandidate getRoute() { return route; }
    public RouteProfile getProfile() { return profile; }
    public double getRiskExposure() { return riskExposure; }
    public double getScore() { return score; }
    public java.util.List<RouteRiskPoint> getRiskPoints() { return riskPoints; }
}
