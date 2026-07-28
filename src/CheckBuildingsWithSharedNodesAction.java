package org.openstreetmap.josm.plugins.mapathonqa;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.swing.AbstractAction;
import javax.swing.JOptionPane;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.tools.I18n;

/**
 * Finds shared nodes between buildings and other objects.
 * Reports the NUMBER OF SHARED NODES and the affected buildings.
 *
 * FIX: Only counts a shared node if at least one of the involved
 * buildings was mapped during the mapathon time window.
 */
public class CheckBuildingsWithSharedNodesAction extends AbstractAction {

    public static class SharedNodeResult {
        public int sharedNodeCount;
        public Set<OsmPrimitive> affectedBuildings;
        public SharedNodeResult(int count, Set<OsmPrimitive> buildings) {
            this.sharedNodeCount = count;
            this.affectedBuildings = buildings;
        }
        public int size() { return sharedNodeCount; }
        public boolean isEmpty() { return sharedNodeCount == 0; }
    }

    public CheckBuildingsWithSharedNodesAction() {
        super(I18n.tr("Select Buildings with Shared Nodes"));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        DataSet ds = MainApplication.getLayerManager().getEditDataSet();
        if (ds == null) { JOptionPane.showMessageDialog(null, "No active OSM data layer found.", "MapathonQA", JOptionPane.WARNING_MESSAGE); return; }
        javax.swing.JDialog prog = CheckNonYesBuildingTagsAction.makeProgress("Checking shared nodes...");
        prog.setVisible(true);
        new javax.swing.SwingWorker<SharedNodeResult, Void>() {
            @Override protected SharedNodeResult doInBackground() { return runOn(ds, null, null); }
            @Override protected void done() {
                prog.dispose();
                try {
                    SharedNodeResult r = get();
                    if (r.isEmpty()) { JOptionPane.showMessageDialog(null, "No buildings with shared nodes found.", "MapathonQA", JOptionPane.INFORMATION_MESSAGE); return; }
                    ds.setSelected(r.affectedBuildings);
                } catch (Exception ex) { prog.dispose(); JOptionPane.showMessageDialog(null, "Check failed:\n"+ex.getMessage(), "MapathonQA", JOptionPane.ERROR_MESSAGE); }
            }
        }.execute();
    }

    public static SharedNodeResult runOn(DataSet ds) {
        return runOn(ds, null, null);
    }

    public static SharedNodeResult runOn(DataSet ds, java.util.Date since) {
        return runOn(ds, since, null);
    }

    public static SharedNodeResult runOn(DataSet ds, java.util.Date since, java.util.Date until) {
        List<Way> buildings = new ArrayList<>();
        for (Way w : ds.getWays()) {
            if (w.isClosed() && w.hasKey("building")) buildings.add(w);
        }
        List<Way> others = new ArrayList<>();
        for (Way w : ds.getWays()) {
            if (!w.hasKey("building")) others.add(w);
        }

        Set<Node> sharedNodes = new LinkedHashSet<>();
        Set<OsmPrimitive> affectedBuildings = new LinkedHashSet<>();
        int nb = buildings.size();

        for (int i = 0; i < nb; i++) {
            Way a = buildings.get(i);
            boolean aInWindow = GeometryUtil.isMappedDuring(a, since, until);

            // Check against other buildings
            for (int j = i + 1; j < nb; j++) {
                Way b = buildings.get(j);
                boolean bInWindow = GeometryUtil.isMappedDuring(b, since, until);
                // FIX: only count shared node if at least one building is in the mapathon window
                if (!aInWindow && !bInWindow) continue;

                for (Node na : a.getNodes()) {
                    if (na == null) continue;
                    for (Node nb2 : b.getNodes()) {
                        if (na == nb2) {
                            sharedNodes.add(na);
                            if (aInWindow) affectedBuildings.add(a);
                            if (bInWindow) affectedBuildings.add(b);
                        }
                    }
                }
            }

            // Check against non-building ways
            if (!aInWindow) continue; // skip if building not in window
            for (Way other : others) {
                for (Node na : a.getNodes()) {
                    if (na == null) continue;
                    for (Node no : other.getNodes()) {
                        if (na == no) {
                            sharedNodes.add(na);
                            affectedBuildings.add(a);
                        }
                    }
                }
            }
        }

        return new SharedNodeResult(sharedNodes.size(), affectedBuildings);
    }
}
