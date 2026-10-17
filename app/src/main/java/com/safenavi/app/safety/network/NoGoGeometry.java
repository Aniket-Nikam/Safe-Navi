package com.safenavi.app.safety.network;

/** Complete segment/buffer intersections; coordinates are [latitude, longitude]. */
public final class NoGoGeometry {
    private NoGoGeometry() { }
    public static double distance(double aLat, double aLon, double bLat, double bLon, double[][] shape, boolean polygon) {
        if (shape.length == 0) return Double.POSITIVE_INFINITY;
        if (shape.length == 1) return pointSegment(shape[0][0], shape[0][1], aLat, aLon, bLat, bLon);
        if (polygon && (inside(aLat, aLon, shape) || inside(bLat, bLon, shape))) return 0;
        double best = Double.POSITIVE_INFINITY;
        int count = polygon ? shape.length : shape.length - 1;
        for (int i = 0; i < count; i++) {
            double[] c = shape[i], d = shape[(i + 1) % shape.length];
            best = Math.min(best, segments(aLat, aLon, bLat, bLon, c[0], c[1], d[0], d[1]));
            if (best == 0) return 0;
        }
        return best;
    }
    private static boolean inside(double lat, double lon, double[][] shape) {
        boolean inside = false;
        for (int i = 0, j = shape.length - 1; i < shape.length; j = i++) {
            double[] a = shape[j], b = shape[i];
            if ((a[0] > lat) != (b[0] > lat) && lon < (b[1] - a[1]) * (lat - a[0]) / (b[0] - a[0]) + a[1]) inside = !inside;
        }
        return inside;
    }
    private static double cross(double ax, double ay, double bx, double by, double cx, double cy) {
        return (bx-ax)*(cy-ay) - (by-ay)*(cx-ax);
    }
    private static boolean on(double ax, double ay, double bx, double by, double px, double py) {
        return Math.abs(cross(ax,ay,bx,by,px,py)) <= 1e-12 && px >= Math.min(ax,bx)-1e-12 && px <= Math.max(ax,bx)+1e-12
                && py >= Math.min(ay,by)-1e-12 && py <= Math.max(ay,by)+1e-12;
    }
    private static double segments(double aLat, double aLon, double bLat, double bLon, double cLat, double cLon, double dLat, double dLon) {
        double cosine = Math.cos(Math.toRadians((aLat+bLat+cLat+dLat)/4));
        double ax=aLon*cosine, bx=bLon*cosine, cx=cLon*cosine, dx=dLon*cosine;
        double first=cross(ax,aLat,bx,bLat,cx,cLat), second=cross(ax,aLat,bx,bLat,dx,dLat);
        double third=cross(cx,cLat,dx,dLat,ax,aLat), fourth=cross(cx,cLat,dx,dLat,bx,bLat);
        if ((first*second < 0 && third*fourth < 0) || on(ax,aLat,bx,bLat,cx,cLat) || on(ax,aLat,bx,bLat,dx,dLat)
                || on(cx,cLat,dx,dLat,ax,aLat) || on(cx,cLat,dx,dLat,bx,bLat)) return 0;
        return Math.min(Math.min(pointSegment(aLat,aLon,cLat,cLon,dLat,dLon), pointSegment(bLat,bLon,cLat,cLon,dLat,dLon)),
                Math.min(pointSegment(cLat,cLon,aLat,aLon,bLat,bLon), pointSegment(dLat,dLon,aLat,aLon,bLat,bLon)));
    }
    private static double pointSegment(double lat, double lon, double aLat, double aLon, double bLat, double bLon) {
        double radius=6371000, cosine=Math.cos(Math.toRadians(lat));
        double px=Math.toRadians(lon)*radius*cosine, py=Math.toRadians(lat)*radius;
        double ax=Math.toRadians(aLon)*radius*cosine, ay=Math.toRadians(aLat)*radius;
        double bx=Math.toRadians(bLon)*radius*cosine, by=Math.toRadians(bLat)*radius;
        double dx=bx-ax, dy=by-ay;
        double t=dx == 0 && dy == 0 ? 0 : Math.max(0,Math.min(1,((px-ax)*dx+(py-ay)*dy)/(dx*dx+dy*dy)));
        return Math.hypot(px-(ax+t*dx),py-(ay+t*dy));
    }
}
