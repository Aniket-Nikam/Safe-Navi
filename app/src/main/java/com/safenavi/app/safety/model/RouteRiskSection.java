package com.safenavi.app.safety.model;

import java.util.*;

public final class RouteRiskSection {
    public final String id, band, area, reason, source;
    public final double risk, latitude, longitude, offset, length;
    public final List<HazardLocation.GeoPoint> geometry;
    public RouteRiskSection(String id,String band,String area,String reason,String source,double risk,
                            double latitude,double longitude,double offset,double length,List<HazardLocation.GeoPoint> geometry) {
        this.id=id;this.band=band;this.area=area;this.reason=reason;this.source=source;this.risk=risk;
        this.latitude=latitude;this.longitude=longitude;this.offset=offset;this.length=length;
        this.geometry=Collections.unmodifiableList(new ArrayList<>(geometry));
    }
}
