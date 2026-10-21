package com.safenavi.app.navigation;

import com.safenavi.app.safety.model.HazardLocation.GeoPoint;
import com.safenavi.app.safety.model.NavigationStep;
import com.safenavi.app.safety.model.RouteCandidate;
import java.util.List;

/** Small foreground/demo progress engine. Does not predict traffic or upload positions. */
public final class NavigationProgress {
    public final RouteCandidate route;
    private final List<GeoPoint> points;
    private final double[] meters;
    private double progress;
    public NavigationProgress(RouteCandidate route) {
        this.route=route; points=route.getGeometry();
        if(points.size()<2) throw new IllegalArgumentException("The selected route has too little geometry.");
        meters=new double[points.size()];
        for(int i=1;i<points.size();i++) meters[i]=meters[i-1]+distance(points.get(i-1),points.get(i));
        if(length()<1) throw new IllegalArgumentException("The selected route has no usable length.");
    }
    public double length() { return meters[meters.length-1]; }
    public double progress() { return progress; }
    public double remainingMeters() { return Math.max(0,route.getDistanceMeters()*(1-progress/length())); }
    public double remainingMinutes() { return Math.max(0,route.getTravelMinutes()*(1-progress/length())); }
    public void advance(double meters) { if(Double.isFinite(meters)) progress=Math.min(length(),Math.max(progress,meters)); }
    public boolean arrived() { return length()-progress<=8; }

    public GeoPoint position() {
        for(int i=1;i<points.size();i++) if(meters[i]>=progress) {
            double fraction=(progress-meters[i-1])/Math.max(.001,meters[i]-meters[i-1]);
            GeoPoint a=points.get(i-1),b=points.get(i);
            return new GeoPoint(a.getLatitude()+(b.getLatitude()-a.getLatitude())*fraction,
                    a.getLongitude()+(b.getLongitude()-a.getLongitude())*fraction);
        }
        return points.get(points.size()-1);
    }
    public int nextStepIndex() {
        List<NavigationStep> steps=route.getNavigationSteps();
        for(int i=1;i<steps.size();i++) if(stepMeters(i)>progress+8) return i;
        return steps.isEmpty()?-1:steps.size()-1;
    }
    public double stepMeters(int index) {
        int point=route.getNavigationSteps().get(index).shapeIndex;
        return meters[Math.max(0,Math.min(meters.length-1,point))];
    }
    public double toNextTurn() { int next=nextStepIndex(); return next<0?length()-progress:Math.max(0,stepMeters(next)-progress); }
    public String instruction() {
        if(arrived()) return "You have reached the end of this route";
        int next=nextStepIndex(); return next<0?"Follow the highlighted route. Turn instructions unavailable.":route.getNavigationSteps().get(next).instruction;
    }
    public double bearing() {
        GeoPoint a=position(); double ahead=Math.min(length(),progress+15);
        int index=1; while(index<meters.length-1 && meters[index]<ahead) index++;
        return bearing(a,points.get(index));
    }
    /** Returns distance from route. Off-route fixes never advance journey progress. */
    public double match(double latitude,double longitude) {
        double nearest=Double.POSITIVE_INFINITY,along=progress;
        double scale=Math.cos(Math.toRadians(latitude)), factor=111195;
        for(int i=1;i<points.size();i++) {
            GeoPoint a=points.get(i-1),b=points.get(i);
            double ax=(a.getLongitude()-longitude)*scale*factor,ay=(a.getLatitude()-latitude)*factor;
            double bx=(b.getLongitude()-longitude)*scale*factor,by=(b.getLatitude()-latitude)*factor;
            double dx=bx-ax,dy=by-ay,denom=dx*dx+dy*dy;
            double t=denom<.001?0:Math.max(0,Math.min(1,-(ax*dx+ay*dy)/denom));
            double separation=Math.hypot(ax+t*dx,ay+t*dy);
            double candidate=meters[i-1]+t*(meters[i]-meters[i-1]);
            if(separation<nearest-.1 || (Math.abs(separation-nearest)<=.1 && Math.abs(candidate-progress)<Math.abs(along-progress))) {
                nearest=separation;along=candidate;
            }
        }
        if(nearest<=60) {
            if(length()-along<=8 && distance(new GeoPoint(latitude,longitude),points.get(points.size()-1))>30) along=Math.max(0,length()-9);
            advance(along);
        }
        return nearest;
    }
    public static double distance(GeoPoint a,GeoPoint b) {
        double lat=Math.toRadians(b.getLatitude()-a.getLatitude()),lon=Math.toRadians(b.getLongitude()-a.getLongitude());
        double h=Math.sin(lat/2)*Math.sin(lat/2)+Math.cos(Math.toRadians(a.getLatitude()))*Math.cos(Math.toRadians(b.getLatitude()))*Math.sin(lon/2)*Math.sin(lon/2);
        return 6371000*2*Math.atan2(Math.sqrt(h),Math.sqrt(Math.max(0,1-h)));
    }
    private static double bearing(GeoPoint a,GeoPoint b) {
        double lat1=Math.toRadians(a.getLatitude()),lat2=Math.toRadians(b.getLatitude()),lon=Math.toRadians(b.getLongitude()-a.getLongitude());
        return (Math.toDegrees(Math.atan2(Math.sin(lon)*Math.cos(lat2),Math.cos(lat1)*Math.sin(lat2)-Math.sin(lat1)*Math.cos(lat2)*Math.cos(lon)))+360)%360;
    }
}
