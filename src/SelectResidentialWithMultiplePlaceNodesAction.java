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
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.tools.I18n;

/** Ported from 3rdPassMM's SelectResidentialWithMultiplePlaceNodesAction. */
public class SelectResidentialWithMultiplePlaceNodesAction extends AbstractAction {

    public SelectResidentialWithMultiplePlaceNodesAction() {
        super(I18n.tr("Select Residential With Multiple Place Nodes"));
    }

    @Override public void actionPerformed(ActionEvent e) {
        DataSet ds = MainApplication.getLayerManager().getEditDataSet();
        if (ds == null) { JOptionPane.showMessageDialog(null, "No active OSM data layer found.", "MapathonQA", JOptionPane.WARNING_MESSAGE); return; }

        List<ResidentialArea> areas = ResidentialArea.collectFromDataSet(ds);
        if (areas.isEmpty()) { JOptionPane.showMessageDialog(null, "No residential areas found in the current layer.", "MapathonQA", JOptionPane.WARNING_MESSAGE); return; }

        List<Node> placeNodes = new ArrayList<>();
        for (Node n : ds.getNodes()) {
            if (n.isDeleted()) continue;
            if (n.hasKey("place")) placeNodes.add(n);
        }
        if (placeNodes.isEmpty()) { JOptionPane.showMessageDialog(null, "No nodes with a place tag found in the current layer.", "MapathonQA", JOptionPane.WARNING_MESSAGE); return; }

        JDialog prog = CheckNonYesBuildingTagsAction.makeProgress("Checking residential areas with multiple place nodes...");
        prog.setVisible(true);
        new SwingWorker<List<OsmPrimitive>, Void>() {
            @Override protected List<OsmPrimitive> doInBackground() { return runOn(areas, placeNodes); }
            @Override protected void done() {
                prog.dispose();
                try {
                    List<OsmPrimitive> flagged = get();
                    if (flagged.isEmpty()) { JOptionPane.showMessageDialog(null, "No residential areas with more than one place node found. No issues found.", "MapathonQA", JOptionPane.INFORMATION_MESSAGE); return; }
                    ds.setSelected(flagged);
                } catch (Exception ex) { JOptionPane.showMessageDialog(null, "Check failed:\n"+ex.getMessage(), "MapathonQA", JOptionPane.ERROR_MESSAGE); }
            }
        }.execute();
    }

    private static List<OsmPrimitive> runOn(List<ResidentialArea> areas, List<Node> placeNodes) {
        List<OsmPrimitive> flagged = new ArrayList<>();
        for (ResidentialArea area : areas) {
            int count = 0;
            for (Node p : placeNodes) {
                if (area.containsNode(p)) {
                    count++;
                    if (count > 1) break;
                }
            }
            if (count > 1) flagged.add(area.primitive);
        }
        return flagged;
    }
}
