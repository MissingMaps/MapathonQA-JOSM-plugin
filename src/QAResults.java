package org.openstreetmap.josm.plugins.mapathonqa;

import java.util.*;
import org.openstreetmap.josm.data.osm.OsmPrimitive;

public class QAResults {
    public int projectId;
    public String mapathonName = "";
    public String startTime, endTime;
    public java.util.Date since, until;

    public int totalBuildings, totalHighways, totalNodes, totalWays, totalRelations;
    public int mapathonBuildings, mapathonHighways;
    public int totalMappers;
    public int issueMappers;

    public List<OsmPrimitive> nonYesBuildingTags = new ArrayList<>();
    public CheckOverlappingBuildingsAction.OverlapResult overlappingBuildings
        = new CheckOverlappingBuildingsAction.OverlapResult(new LinkedHashSet<>(), 0, 0);
    public Set<OsmPrimitive> buildingsOnHighways  = new LinkedHashSet<>();
    public List<OsmPrimitive> nonOrthogonalBuildings = new ArrayList<>();
    public List<OsmPrimitive> buildingsWithLayerTag  = new ArrayList<>();
    public CheckBuildingsWithSharedNodesAction.SharedNodeResult buildingsWithSharedNodes
        = new CheckBuildingsWithSharedNodesAction.SharedNodeResult(0, new LinkedHashSet<>());
    public List<OsmPrimitive> untaggedObjects = new ArrayList<>();

    public Set<OsmPrimitive> allFlagged() {
        Set<OsmPrimitive> all = new LinkedHashSet<>();
        for (OsmPrimitive p : nonYesBuildingTags)    if (p!=null) all.add(p);
        for (OsmPrimitive p : overlappingBuildings.flaggedBuildings) if (p!=null) all.add(p);
        for (OsmPrimitive p : buildingsOnHighways)    if (p!=null) all.add(p);
        for (OsmPrimitive p : nonOrthogonalBuildings) if (p!=null) all.add(p);
        for (OsmPrimitive p : buildingsWithLayerTag)  if (p!=null) all.add(p);
        for (OsmPrimitive p : buildingsWithSharedNodes.affectedBuildings) if (p!=null) all.add(p);
        for (OsmPrimitive p : untaggedObjects)        if (p!=null) all.add(p);
        return all;
    }

    public int totalIssueOverride = -1; // if >= 0, used instead of allFlagged().size() (for demo)
    public int totalIssues() { return totalIssueOverride >= 0 ? totalIssueOverride : allFlagged().size(); }

    public double qualityScore() {
        int relevant = totalBuildings + totalHighways;
        if (relevant == 0) return 100.0;
        return Math.max(0.0, (1.0 - Math.min(totalIssues(), relevant) / (double) relevant) * 100.0);
    }

    public String qualityLabel() {
        double q = qualityScore();
        if (q >= 95) return "Excellent";
        if (q >= 85) return "Good";
        if (q >= 70) return "Acceptable";
        if (q >= 50) return "Needs improvement";
        return "Poor";
    }

    // "Without issues" uses mapathon (in-window) counts when a time window was set, else totals
    public int mapathonFeatures() {
        return since != null ? (mapathonBuildings + mapathonHighways) : (totalBuildings + totalHighways);
    }

    public int cleanCount() {
        return Math.max(0, mapathonFeatures() - totalIssues());
    }
}
