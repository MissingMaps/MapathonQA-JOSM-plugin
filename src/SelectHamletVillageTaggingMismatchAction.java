package org.openstreetmap.josm.plugins.mapathonqa;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.tools.I18n;

/**
 * Flags place=hamlet/place=village nodes whose enclosing landuse=residential area's building
 * count doesn't match that classification: hamlet expects fewer than 15 buildings inside the
 * area, village expects 15 or more. Sibling check to SelectResidentialWithMultiplePlaceNodesAction
 * (which flags too many place nodes in one area) - this one flags a single place node whose
 * hamlet/village tag disagrees with the area's actual building count.
 *
 * Uses DataSet.searchNodes()/searchWays() (JOSM's own spatial index) scoped to each area's
 * bounding box, instead of scanning every place node/building in the layer against every area -
 * keeps this usable on full-project-sized downloads with many residential areas.
 */
public class SelectHamletVillageTaggingMismatchAction extends AbstractAction {

    private static final int VILLAGE_THRESHOLD = 15;

    public SelectHamletVillageTaggingMismatchAction() {
        super(I18n.tr("Select Hamlet/Village Tagging Mismatch"));
    }

    @Override public void actionPerformed(ActionEvent e) {
        DataSet ds = MainApplication.getLayerManager().getEditDataSet();
        if (ds == null) { JOptionPane.showMessageDialog(null, "No active OSM data layer found.", "MapathonQA", JOptionPane.WARNING_MESSAGE); return; }

        List<ResidentialArea> areas = ResidentialArea.collectFromDataSet(ds);
        if (areas.isEmpty()) { JOptionPane.showMessageDialog(null, "No residential areas found in the current layer.", "MapathonQA", JOptionPane.WARNING_MESSAGE); return; }

        Set<Node> placeNodes = new HashSet<>();
        for (Node n : ds.getNodes()) {
            if (n.isDeleted()) continue;
            String place = n.get("place");
            if ("hamlet".equals(place) || "village".equals(place)) placeNodes.add(n);
        }
        if (placeNodes.isEmpty()) { JOptionPane.showMessageDialog(null, "No place=hamlet or place=village nodes found in the current layer.", "MapathonQA", JOptionPane.WARNING_MESSAGE); return; }

        Set<Way> buildings = new HashSet<>();
        for (Way w : ds.getWays()) {
            if (w.isDeleted() || w.isIncomplete()) continue;
            if (w.isClosed() && w.hasKey("building") && !"no".equals(w.get("building")) && !"entrance".equals(w.get("building"))) buildings.add(w);
        }
        if (buildings.isEmpty()) { JOptionPane.showMessageDialog(null, "No buildings found in the current layer.", "MapathonQA", JOptionPane.WARNING_MESSAGE); return; }

        JDialog prog = CheckNonYesBuildingTagsAction.makeProgress("Checking hamlet/village building counts...");
        prog.setVisible(true);
        new SwingWorker<List<OsmPrimitive>, Void>() {
            @Override protected List<OsmPrimitive> doInBackground() { return runOn(ds, areas, placeNodes, buildings); }
            @Override protected void done() {
                prog.dispose();
                try {
                    List<OsmPrimitive> flagged = get();
                    if (flagged.isEmpty()) { JOptionPane.showMessageDialog(null, "No hamlet/village size mismatches found. No issues found.", "MapathonQA", JOptionPane.INFORMATION_MESSAGE); return; }
                    ds.setSelected(flagged);
                } catch (Exception ex) { JOptionPane.showMessageDialog(null, "Check failed:\n"+ex.getMessage(), "MapathonQA", JOptionPane.ERROR_MESSAGE); }
            }
        }.execute();
    }

    private static List<OsmPrimitive> runOn(DataSet ds, List<ResidentialArea> areas, Set<Node> placeNodes, Set<Way> buildings) {
        List<OsmPrimitive> flagged = new ArrayList<>();
        for (ResidentialArea area : areas) {
            BBox bbox = area.getBBox();

            List<Node> placesInArea = new ArrayList<>();
            for (Node p : ds.searchNodes(bbox)) {
                if (placeNodes.contains(p) && area.containsNode(p)) placesInArea.add(p);
            }
            if (placesInArea.isEmpty()) continue;

            int buildingCount = 0;
            for (Way w : ds.searchWays(bbox)) {
                if (buildings.contains(w) && buildingInArea(area, w)) buildingCount++;
            }

            for (Node p : placesInArea) {
                boolean isHamlet = "hamlet".equals(p.get("place"));
                boolean mismatch = isHamlet ? buildingCount >= VILLAGE_THRESHOLD : buildingCount < VILLAGE_THRESHOLD;
                if (mismatch) flagged.add(p);
            }
        }
        return flagged;
    }

    /** A building counts as inside the area if any of its own nodes falls inside it. */
    private static boolean buildingInArea(ResidentialArea area, Way building) {
        for (Node n : building.getNodes()) {
            if (area.containsNode(n)) return true;
        }
        return false;
    }
}
