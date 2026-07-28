package org.openstreetmap.josm.plugins.mapathonqa;
import java.awt.BorderLayout; import java.awt.event.ActionEvent;
import java.util.*; import javax.swing.*;
import org.openstreetmap.josm.data.osm.*;
import org.openstreetmap.josm.gui.MainApplication; import org.openstreetmap.josm.tools.I18n;
import org.openstreetmap.josm.tools.Geometry;
public class CheckOverlappingBuildingsAction extends AbstractAction {

    /**
     * Overlapping/contained buildings, plus how many of them are exact duplicates (see
     * GeometryUtil.isExactDuplicate). size() reports the number of overlapping PAIRS found
     * (matching the report note "each count = one pair") - not the number of distinct flagged
     * buildings, since one building can be involved in more than one overlapping pair.
     * flaggedBuildings is still the deduplicated set actually selected in the editor.
     */
    public static class OverlapResult {
        public Set<OsmPrimitive> flaggedBuildings;
        public int duplicateBuildingCount;
        public int pairCount;
        public OverlapResult(Set<OsmPrimitive> flaggedBuildings, int duplicateBuildingCount, int pairCount) {
            this.flaggedBuildings = flaggedBuildings;
            this.duplicateBuildingCount = duplicateBuildingCount;
            this.pairCount = pairCount;
        }
        public int size() { return pairCount; }
        public boolean isEmpty() { return flaggedBuildings.isEmpty(); }
    }

    public CheckOverlappingBuildingsAction() { super(I18n.tr("Select Overlapping Buildings")); }
    @Override public void actionPerformed(ActionEvent e) {
        DataSet ds = MainApplication.getLayerManager().getEditDataSet();
        if (ds == null) { JOptionPane.showMessageDialog(null, "No active OSM data layer found.", "MapathonQA", JOptionPane.WARNING_MESSAGE); return; }
        JDialog prog = CheckNonYesBuildingTagsAction.makeProgress("Checking overlapping buildings...");
        prog.setVisible(true);
        new SwingWorker<OverlapResult, Void>() {
            @Override protected OverlapResult doInBackground() { return runOn(ds, null, null); }
            @Override protected void done() {
                prog.dispose();
                try {
                    OverlapResult r = get();
                    if (r.isEmpty()) { JOptionPane.showMessageDialog(null, "No overlapping buildings found.", "MapathonQA", JOptionPane.INFORMATION_MESSAGE); return; }
                    ds.setSelected(r.flaggedBuildings);
                } catch (Exception ex) { prog.dispose(); JOptionPane.showMessageDialog(null, "Check failed:\n"+ex.getMessage(), "MapathonQA", JOptionPane.ERROR_MESSAGE); }
            }
        }.execute();
    }
    public static OverlapResult runOn(DataSet ds) { return runOn(ds, null, null); }
    public static OverlapResult runOn(DataSet ds, java.util.Date since) { return runOn(ds, since, null); }
    public static OverlapResult runOn(DataSet ds, java.util.Date since, java.util.Date until) {
        List<Way> buildings = new ArrayList<>();
        for (Way w : ds.getWays()) if (w.isClosed()&&w.hasKey("building")&&w.getNodesCount()>=4
                && !"no".equals(w.get("building")) && !"entrance".equals(w.get("building"))) buildings.add(w);
        Set<OsmPrimitive> f = new LinkedHashSet<>();
        Set<OsmPrimitive> duplicates = new LinkedHashSet<>();
        int pairCount = 0;
        int n=buildings.size();
        for (int i=0;i<n;i++) { Way a=buildings.get(i); for (int j=i+1;j<n;j++) { Way b=buildings.get(j);
            if (GeometryUtil.bboxDisjoint(a,b)) continue;
            boolean dup = GeometryUtil.isExactDuplicate(a,b);
            // Same classification JOSM's own validator uses for "Overlapping buildings" /
            // "Building inside building" (Geometry.polygonIntersection on the two ways' Areas) -
            // CROSSING is a true partial overlap, FIRST/SECOND_INSIDE is one fully containing the
            // other. Both count here (this check folds both concepts into one row), but the
            // CROSSING subset alone now matches JOSM's Validation Results count exactly, instead of
            // the old segment/vertex heuristic which over-counted relative to it.
            Geometry.PolygonIntersection classification = dup ? null : GeometryUtil.classifyOverlap(a,b);
            boolean overlapping = classification == Geometry.PolygonIntersection.CROSSING
                || classification == Geometry.PolygonIntersection.FIRST_INSIDE_SECOND
                || classification == Geometry.PolygonIntersection.SECOND_INSIDE_FIRST;
            if (dup || overlapping) {
                OsmPrimitive flagged = null;
                if (GeometryUtil.isMappedDuring(a,since,until)) flagged=a; else if (GeometryUtil.isMappedDuring(b,since,until)) flagged=b;
                if (flagged!=null) { f.add(flagged); pairCount++; if (dup) duplicates.add(flagged); }
            }
        }}
        return new OverlapResult(f, duplicates.size(), pairCount);
    }
}
