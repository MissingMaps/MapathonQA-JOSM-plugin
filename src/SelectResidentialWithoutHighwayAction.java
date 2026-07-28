package org.openstreetmap.josm.plugins.mapathonqa;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.tools.I18n;

/** Ported from 3rdPassMM's SelectResidentialWithoutHighwayAction. */
public class SelectResidentialWithoutHighwayAction extends AbstractAction {

    public SelectResidentialWithoutHighwayAction() {
        super(I18n.tr("Select Residential Without Highway"));
    }

    @Override public void actionPerformed(ActionEvent e) {
        DataSet ds = MainApplication.getLayerManager().getEditDataSet();
        if (ds == null) { JOptionPane.showMessageDialog(null, "No active OSM data layer found.", "MapathonQA", JOptionPane.WARNING_MESSAGE); return; }

        List<ResidentialArea> areas = ResidentialArea.collectFromDataSet(ds);
        if (areas.isEmpty()) { JOptionPane.showMessageDialog(null, "No residential areas found in the current layer.", "MapathonQA", JOptionPane.WARNING_MESSAGE); return; }

        List<Way> highways = new ArrayList<>();
        for (Way w : ds.getWays()) {
            if (w.isDeleted() || w.isIncomplete()) continue;
            if (w.hasKey("highway")) highways.add(w);
        }
        if (highways.isEmpty()) { JOptionPane.showMessageDialog(null, "No highway ways found in the current layer.", "MapathonQA", JOptionPane.WARNING_MESSAGE); return; }

        JDialog prog = CheckNonYesBuildingTagsAction.makeProgress("Checking residential areas without a highway...");
        prog.setVisible(true);
        new SwingWorker<List<OsmPrimitive>, Void>() {
            @Override protected List<OsmPrimitive> doInBackground() { return runOn(areas, highways); }
            @Override protected void done() {
                prog.dispose();
                try {
                    List<OsmPrimitive> flagged = get();
                    if (flagged.isEmpty()) { JOptionPane.showMessageDialog(null, "All residential areas have a highway going through them. No issues found.", "MapathonQA", JOptionPane.INFORMATION_MESSAGE); return; }
                    ds.setSelected(flagged);
                } catch (Exception ex) { JOptionPane.showMessageDialog(null, "Check failed:\n"+ex.getMessage(), "MapathonQA", JOptionPane.ERROR_MESSAGE); }
            }
        }.execute();
    }

    private static List<OsmPrimitive> runOn(List<ResidentialArea> areas, List<Way> highways) {
        List<OsmPrimitive> flagged = new ArrayList<>();
        for (ResidentialArea area : areas) {
            boolean hasHighway = false;
            for (Way hw : highways) {
                List<Node> nodes = hw.getNodes();
                for (Node node : nodes) {
                    if (area.containsNode(node)) { hasHighway = true; break; }
                }
                if (!hasHighway) {
                    for (int i = 0; i < nodes.size() - 1; i++) {
                        if (area.intersectsSegment(nodes.get(i), nodes.get(i + 1))) { hasHighway = true; break; }
                    }
                }
                if (hasHighway) break;
            }
            if (!hasHighway) flagged.add(area.primitive);
        }
        return flagged;
    }
}
