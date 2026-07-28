package org.openstreetmap.josm.plugins.mapathonqa;

import java.awt.geom.Area;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.tools.Geometry;

public class GeometryUtil {

    public static boolean nodeInsidePolygon(double lat, double lon, List<Node> ring) {
        int n = ring.size();
        boolean inside = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            Node ni = ring.get(i); Node nj = ring.get(j);
            if (ni == null || nj == null) continue;
            double xi = ni.lon(), yi = ni.lat(), xj = nj.lon(), yj = nj.lat();
            if (((yi > lat) != (yj > lat)) && (lon < (xj - xi) * (lat - yi) / (yj - yi) + xi))
                inside = !inside;
        }
        return inside;
    }

    public static boolean segmentsIntersect(Node a1, Node a2, Node b1, Node b2) {
        if (a1 == null || a2 == null || b1 == null || b2 == null) return false;
        double ax1=a1.lon(),ay1=a1.lat(),ax2=a2.lon(),ay2=a2.lat();
        double bx1=b1.lon(),by1=b1.lat(),bx2=b2.lon(),by2=b2.lat();
        double d1=cross(bx2-bx1,by2-by1,ax1-bx1,ay1-by1);
        double d2=cross(bx2-bx1,by2-by1,ax2-bx1,ay2-by1);
        double d3=cross(ax2-ax1,ay2-ay1,bx1-ax1,by1-ay1);
        double d4=cross(ax2-ax1,ay2-ay1,bx2-ax1,by2-ay1);
        if (((d1>0&&d2<0)||(d1<0&&d2>0))&&((d3>0&&d4<0)||(d3<0&&d4>0))) return true;
        return false;
    }

    private static double cross(double ux,double uy,double vx,double vy) { return ux*vy-uy*vx; }

    /**
     * Classifies how two building ways' areas relate, using JOSM's own
     * Geometry.polygonIntersection (the same Area-based math and 1e-4 epsilon
     * behind the built-in validator's "Overlapping buildings"/"Building inside
     * building" MapCSS rules), so our counts agree with JOSM's Validation
     * Results panel instead of a hand-rolled segment/vertex heuristic that
     * over-counted. Returns null for degenerate (empty-area) ways.
     */
    public static Geometry.PolygonIntersection classifyOverlap(Way a, Way b) {
        Area areaA = Geometry.getAreaEastNorth(a);
        Area areaB = Geometry.getAreaEastNorth(b);
        if (areaA == null || areaB == null || areaA.isEmpty() || areaB.isEmpty()) return null;
        return Geometry.polygonIntersection(areaA, areaB, 1.0E-4);
    }

    public static boolean highwayCrossesBuilding(Way highway, Way building) {
        List<Node> hn=highway.getNodes(), bn=building.getNodes();
        int hs=hn.size(), bs=bn.size();
        if (hs<2||bs<3) return false;
        for (int i=0;i<hs-1;i++) for (int j=0;j<bs-1;j++)
            if (segmentsIntersect(hn.get(i),hn.get(i+1),bn.get(j),bn.get(j+1))) return true;
        for (Node n:hn) if (n!=null&&nodeInsidePolygon(n.lat(),n.lon(),bn)) return true;
        return false;
    }

    public static boolean bboxDisjoint(Way a, Way b) {
        double[] ba=bbox(a), bb=bbox(b);
        if (ba==null||bb==null) return true;
        return ba[1]<bb[0]||bb[1]<ba[0]||ba[3]<bb[2]||bb[3]<ba[2];
    }

    private static double[] bbox(Way w) {
        List<Node> nodes=w.getNodes();
        if (nodes.isEmpty()) return null;
        double minLat=Double.MAX_VALUE,maxLat=-Double.MAX_VALUE;
        double minLon=Double.MAX_VALUE,maxLon=-Double.MAX_VALUE;
        for (Node n:nodes) {
            if (n==null) continue;
            if (n.lat()<minLat) minLat=n.lat(); if (n.lat()>maxLat) maxLat=n.lat();
            if (n.lon()<minLon) minLon=n.lon(); if (n.lon()>maxLon) maxLon=n.lon();
        }
        return new double[]{minLat,maxLat,minLon,maxLon};
    }

    public static boolean isMappedDuring(org.openstreetmap.josm.data.osm.OsmPrimitive p,
                                          java.util.Date since) {
        return isMappedDuring(p, since, null);
    }

    public static boolean isMappedDuring(org.openstreetmap.josm.data.osm.OsmPrimitive p,
                                          java.util.Date since, java.util.Date until) {
        if (since == null) return true;
        int raw = p.getRawTimestamp();
        if (raw == 0) return true;
        long sinceSeconds = since.getTime() / 1000L;
        if (raw < sinceSeconds) return false;
        if (until != null) {
            long untilSeconds = until.getTime() / 1000L;
            if (raw > untilSeconds) return false;
        }
        return true;
    }

    /**
     * True if a and b are closed ways tracing the exact same ring of corner
     * coordinates (same set of points, regardless of starting node or winding
     * direction) - e.g. a building accidentally duplicated via copy/paste.
     * Coordinates must match exactly, so two independent manual traces of the
     * same real-world building (which never land on identical lat/lon) won't
     * be flagged here - only true duplicates.
     */
    public static boolean isExactDuplicate(Way a, Way b) {
        List<Node> an = a.getNodes(), bn = b.getNodes();
        int as = an.size(), bs = bn.size();
        if (as != bs || as < 4) return false;
        Set<LatLon> aSet = new HashSet<>(), bSet = new HashSet<>();
        for (int i = 0; i < as - 1; i++) {
            Node nd = an.get(i);
            if (nd == null || nd.getCoor() == null) return false;
            aSet.add(nd.getCoor());
        }
        for (int i = 0; i < bs - 1; i++) {
            Node nd = bn.get(i);
            if (nd == null || nd.getCoor() == null) return false;
            bSet.add(nd.getCoor());
        }
        return aSet.equals(bSet);
    }

    public static boolean waysShareNode(Way a, Way b) {
        for (Node na : a.getNodes()) {
            if (na == null) continue;
            for (Node nb : b.getNodes()) { if (na == nb) return true; }
        }
        return false;
    }
}
