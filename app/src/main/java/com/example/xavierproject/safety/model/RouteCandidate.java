package com.example.xavierproject.safety.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RouteCandidate {
    private final String providerRouteId;
    private final double travelMinutes;
    private final double distanceMeters;
    private final List<HazardLocation.GeoPoint> geometry;

    public RouteCandidate(String providerRouteId, double travelMinutes, double distanceMeters,
                          List<HazardLocation.GeoPoint> geometry) {
        this.providerRouteId = providerRouteId;
        this.travelMinutes = travelMinutes;
        this.distanceMeters = distanceMeters;
        this.geometry = new ArrayList<>(geometry);
    }

    public String getProviderRouteId() { return providerRouteId; }
    public double getTravelMinutes() { return travelMinutes; }
    public double getDistanceMeters() { return distanceMeters; }
    public List<HazardLocation.GeoPoint> getGeometry() { return Collections.unmodifiableList(geometry); }
}
