package org.openstreetmap.josm.plugins.mapathonqa;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.HashSet;
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
 *
 * Uses Node.getParentWays() (JOSM's own live node-to-way index) instead of comparing
 * every pair of buildings' node lists - turns an O(buildings^2 x nodes^2) scan into a
 * single pass over each building's nodes, since JOSM already tracks which ways use a node.
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

        Set<Node> visited = new HashSet<>();
        Set<Node> sharedNodes = new LinkedHashSet<>();
        Set<OsmPrimitive> affectedBuildings = new LinkedHashSet<>();

        for (Way bld : buildings) {
            for (Node n : bld.getNodes()) {
                if (n == null || !visited.add(n)) continue; // skip nulls and nodes already resolved via another building

                List<Way> parents = n.getParentWays();
                if (parents.size() < 2) continue; // only this one way uses the node - nothing shared

                // Same building/other split the old buildings/others lists used, just applied
                // to this node's actual parent ways instead of the whole dataset.
                List<Way> pBuildings = new ArrayList<>();
                List<Way> pOthers = new ArrayList<>();
                for (Way p : parents) {
                    if (p.isClosed() && p.hasKey("building")) pBuildings.add(p);
                    else if (!p.hasKey("building")) pOthers.add(p);
                }

                boolean qualifies = false;
                for (int x = 0; x < pBuildings.size(); x++) {
                    Way a = pBuildings.get(x);
                    boolean aInWindow = GeometryUtil.isMappedDuring(a, since, until);

                    // Building-vs-building: counted if at least one side is in the mapathon window
                    for (int y = x + 1; y < pBuildings.size(); y++) {
                        Way b = pBuildings.get(y);
                        boolean bInWindow = GeometryUtil.isMappedDuring(b, since, until);
                        if (!aInWindow && !bInWindow) continue;
                        qualifies = true;
                        if (aInWindow) affectedBuildings.add(a);
                        if (bInWindow) affectedBuildings.add(b);
                    }

                    // Building-vs-other: only counted if the building itself is in the window
                    if (aInWindow && !pOthers.isEmpty()) {
                        qualifies = true;
                        affectedBuildings.add(a);
                    }
                }

                if (qualifies) sharedNodes.add(n);
            }
        }

        return new SharedNodeResult(sharedNodes.size(), affectedBuildings);
    }
}
