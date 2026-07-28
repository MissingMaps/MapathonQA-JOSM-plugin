package org.openstreetmap.josm.plugins.mapathonqa;

import java.util.ArrayList;
import java.util.List;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;

/**
 * A landuse=residential area, from either a closed way or a multipolygon relation
 * (outer/blank-role members stitched into ring(s); inner/hole members are ignored).
 * Ported from 3rdPassMM's ResidentialArea/GeometryUtil (see SelectResidentialWithoutHighwayAction
 * and SelectResidentialWithMultiplePlaceNodesAction), reusing this plugin's own GeometryUtil
 * for the point-in-polygon/segment-intersection math instead of a second copy of it.
 */
final class ResidentialArea {

    final OsmPrimitive primitive;
    final List<List<Node>> outerRings;

    private ResidentialArea(OsmPrimitive primitive, List<List<Node>> outerRings) {
        this.primitive = primitive;
        this.outerRings = outerRings;
    }

    static List<ResidentialArea> collectFromDataSet(DataSet ds) {
        List<ResidentialArea> result = new ArrayList<>();

        for (Way w : ds.getWays()) {
            if (w.isDeleted() || w.isIncomplete()) continue;
            if (!w.isClosed()) continue;
            if (!"residential".equals(w.get("landuse"))) continue;
            List<List<Node>> rings = new ArrayList<>();
            rings.add(w.getNodes());
            result.add(new ResidentialArea(w, rings));
        }

        for (Relation r : ds.getRelations()) {
            if (r.isDeleted() || r.isIncomplete()) continue;
            if (!"multipolygon".equals(r.get("type"))) continue;
            if (!"residential".equals(r.get("landuse"))) continue;

            List<Way> outerWays = new ArrayList<>();
            for (RelationMember m : r.getMembers()) {
                if (!m.isWay()) continue;
                String role = m.getRole();
                if (!"outer".equals(role) && !"".equals(role)) continue;
                Way mw = m.getWay();
                if (mw == null || mw.isDeleted() || mw.isIncomplete()) continue;
                outerWays.add(mw);
            }
            List<List<Node>> rings = stitchWaysIntoRings(outerWays);
            if (!rings.isEmpty()) result.add(new ResidentialArea(r, rings));
        }
        return result;
    }

    /** Chains way node-lists end-to-end (matching by node identity) into closed rings. Open/unstitchable chains are dropped. */
    private static List<List<Node>> stitchWaysIntoRings(List<Way> ways) {
        List<List<Node>> rings = new ArrayList<>();
        List<Way> remaining = new ArrayList<>(ways);
        while (!remaining.isEmpty()) {
            Way w = remaining.remove(0);
            List<Node> ring = new ArrayList<>(w.getNodes());
            if (w.isClosed()) { rings.add(ring); continue; }

            boolean changed = true;
            while (changed && !remaining.isEmpty()) {
                changed = false;
                Node ringLast = ring.get(ring.size() - 1);
                for (int i = 0; i < remaining.size(); i++) {
                    Way cand = remaining.get(i);
                    List<Node> cn = cand.getNodes();
                    Node candFirst = cn.get(0);
                    Node candLast  = cn.get(cn.size() - 1);
                    if (candFirst == ringLast) {
                        for (int k = 1; k < cn.size(); k++) ring.add(cn.get(k));
                        remaining.remove(i); changed = true; break;
                    } else if (candLast == ringLast) {
                        for (int k = cn.size() - 2; k >= 0; k--) ring.add(cn.get(k));
                        remaining.remove(i); changed = true; break;
                    }
                }
                if (ring.get(0) == ring.get(ring.size() - 1)) break;
            }
            if (ring.size() >= 3 && ring.get(0) == ring.get(ring.size() - 1)) {
                rings.add(ring);
            }
        }
        return rings;
    }

    boolean containsNode(Node n) {
        if (n == null || n.getCoor() == null) return false;
        for (List<Node> ring : outerRings) {
            if (GeometryUtil.nodeInsidePolygon(n.lat(), n.lon(), ring)) return true;
        }
        return false;
    }

    boolean intersectsSegment(Node a, Node b) {
        for (List<Node> ring : outerRings) {
            for (int i = 0; i < ring.size() - 1; i++) {
                if (GeometryUtil.segmentsIntersect(a, b, ring.get(i), ring.get(i + 1))) return true;
            }
        }
        return false;
    }
}
