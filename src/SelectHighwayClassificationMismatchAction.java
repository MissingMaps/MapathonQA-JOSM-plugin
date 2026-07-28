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
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.tools.I18n;

/**
 * Ported from 3rdPassMM's SelectHighwayClassificationMismatchAction. Flags a highway way
 * whose two endpoints each connect end-to-end to other highway(s) of one single, consistent,
 * differing classification, and that differing classification is the same at both ends -
 * e.g. a short "unclassified" segment sandwiched between two "path" segments.
 */
public class SelectHighwayClassificationMismatchAction extends AbstractAction {

    public SelectHighwayClassificationMismatchAction() {
        super(I18n.tr("Select Highway Classification Mismatch"));
    }

    @Override public void actionPerformed(ActionEvent e) {
        DataSet ds = MainApplication.getLayerManager().getEditDataSet();
        if (ds == null) { JOptionPane.showMessageDialog(null, "No active OSM data layer found.", "MapathonQA", JOptionPane.WARNING_MESSAGE); return; }

        List<Way> highways = new ArrayList<>();
        for (Way w : ds.getWays()) {
            if (w.isDeleted() || w.isIncomplete()) continue;
            if (w.hasKey("highway")) highways.add(w);
        }
        if (highways.size() < 3) { JOptionPane.showMessageDialog(null, "Not enough highway ways found in the current layer to check.", "MapathonQA", JOptionPane.WARNING_MESSAGE); return; }

        JDialog prog = CheckNonYesBuildingTagsAction.makeProgress("Checking highway classification mismatches...");
        prog.setVisible(true);
        new SwingWorker<List<Way>, Void>() {
            @Override protected List<Way> doInBackground() { return runOn(highways); }
            @Override protected void done() {
                prog.dispose();
                try {
                    List<Way> mismatches = get();
                    if (mismatches.isEmpty()) { JOptionPane.showMessageDialog(null, "No highway classification mismatches found. No issues found.", "MapathonQA", JOptionPane.INFORMATION_MESSAGE); return; }
                    ds.setSelected(mismatches);
                } catch (Exception ex) { JOptionPane.showMessageDialog(null, "Check failed:\n"+ex.getMessage(), "MapathonQA", JOptionPane.ERROR_MESSAGE); }
            }
        }.execute();
    }

    private static List<Way> runOn(List<Way> highways) {
        List<Way> mismatches = new ArrayList<>();
        for (Way w : highways) {
            String cls = w.get("highway");
            if (cls == null) continue;
            List<Node> nodes = w.getNodes();
            if (nodes.size() < 2) continue;
            Node first = nodes.get(0);
            Node last  = nodes.get(nodes.size() - 1);

            String diffAtFirst = findDifferingNeighborClass(w, cls, first, highways);
            String diffAtLast  = findDifferingNeighborClass(w, cls, last, highways);

            if (diffAtFirst != null && diffAtLast != null && diffAtFirst.equals(diffAtLast)) {
                mismatches.add(w);
            }
        }
        return mismatches;
    }

    /**
     * Among other highway ways that connect to {@code endpoint} via their OWN first/last node
     * (a true end-to-end junction, not merely crossing through it) and whose highway= value
     * differs from {@code wClass}, returns that differing value - only if every such neighbor
     * agrees on the same one; returns null if there is none, or if they disagree (ambiguous).
     */
    private static String findDifferingNeighborClass(Way w, String wClass, Node endpoint, List<Way> allHighways) {
        String result = null;
        for (Way other : allHighways) {
            if (other == w) continue;
            List<Node> otherNodes = other.getNodes();
            if (otherNodes.size() < 2) continue;
            Node otherFirst = otherNodes.get(0);
            Node otherLast  = otherNodes.get(otherNodes.size() - 1);

            if (otherFirst != endpoint && otherLast != endpoint) continue;

            String otherClass = other.get("highway");
            if (otherClass == null) continue;
            if (otherClass.equals(wClass)) continue;

            if (result == null) {
                result = otherClass;
            } else if (!result.equals(otherClass)) {
                return null;
            }
        }
        return result;
    }
}
