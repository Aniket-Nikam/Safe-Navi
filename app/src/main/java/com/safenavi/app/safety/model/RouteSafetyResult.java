package com.safenavi.app.safety.model;

public class RouteSafetyResult {
    private final RouteCandidate route;
    private final RouteProfile profile;
    private final double riskExposure;
    private final double score;
    private final java.util.List<RouteRiskPoint> riskPoints;
    private final java.util.List<RouteRiskSection> riskSections;

    public RouteSafetyResult(RouteCandidate route, RouteProfile profile, double riskExposure, double score) {
        this(route, profile, riskExposure, score, java.util.Collections.emptyList());
    }

    public RouteSafetyResult(RouteCandidate route, RouteProfile profile, double riskExposure, double score,
                             java.util.List<RouteRiskPoint> riskPoints) {
        this(route,profile,riskExposure,score,riskPoints,java.util.Collections.emptyList());
    }
    public RouteSafetyResult(RouteCandidate route, RouteProfile profile, double riskExposure, double score,
                             java.util.List<RouteRiskPoint> riskPoints, java.util.List<RouteRiskSection> riskSections) {
        this.route = route;
        this.profile = profile;
        this.riskExposure = riskExposure;
        this.score = score;
        this.riskPoints = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(riskPoints));
        this.riskSections = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(riskSections));
    }

    public RouteCandidate getRoute() { return route; }
    public RouteProfile getProfile() { return profile; }
    public double getRiskExposure() { return riskExposure; }
    public double getScore() { return score; }
    public java.util.List<RouteRiskPoint> getRiskPoints() { return riskPoints; }
    public java.util.List<RouteRiskSection> getRiskSections() { return riskSections; }
}
