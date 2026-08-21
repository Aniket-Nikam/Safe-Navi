package com.safenavi.app.safety.model;

public class RouteSafetyResult {
    private final RouteCandidate route;
    private final RouteProfile profile;
    private final double riskExposure;
    private final double score;

    public RouteSafetyResult(RouteCandidate route, RouteProfile profile, double riskExposure, double score) {
        this.route = route;
        this.profile = profile;
        this.riskExposure = riskExposure;
        this.score = score;
    }

    public RouteCandidate getRoute() { return route; }
    public RouteProfile getProfile() { return profile; }
    public double getRiskExposure() { return riskExposure; }
    public double getScore() { return score; }
}
