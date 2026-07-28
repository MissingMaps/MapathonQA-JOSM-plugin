package org.openstreetmap.josm.plugins.mapathonqa;
import java.awt.BorderLayout; import java.awt.event.ActionEvent;
import java.util.*; import javax.swing.*;
import org.openstreetmap.josm.data.osm.*;
import org.openstreetmap.josm.gui.MainApplication; import org.openstreetmap.josm.tools.I18n;
public class CheckBuildingsOnHighwaysAction extends AbstractAction {
    public CheckBuildingsOnHighwaysAction() { super(I18n.tr("Select Buildings on Highways")); }
    @Override public void actionPerformed(ActionEvent e) {
        DataSet ds = MainApplication.getLayerManager().getEditDataSet();
        if (ds == null) { JOptionPane.showMessageDialog(null, "No active OSM data layer found.", "MapathonQA", JOptionPane.WARNING_MESSAGE); return; }
        JDialog prog = CheckNonYesBuildingTagsAction.makeProgress("Checking buildings on highways...");
        prog.setVisible(true);
        new SwingWorker<Set<OsmPrimitive>, Void>() {
            @Override protected Set<OsmPrimitive> doInBackground() { return runOn(ds, null, null); }
            @Override protected void done() {
                prog.dispose();
                try {
                    Set<OsmPrimitive> f = get();
                    if (f.isEmpty()) { JOptionPane.showMessageDialog(null, "No buildings found overlapping with highways.", "MapathonQA", JOptionPane.INFORMATION_MESSAGE); return; }
                    ds.setSelected(f);
                } catch (Exception ex) { prog.dispose(); JOptionPane.showMessageDialog(null, "Check failed:\n"+ex.getMessage(), "MapathonQA", JOptionPane.ERROR_MESSAGE); }
            }
        }.execute();
    }
    public static Set<OsmPrimitive> runOn(DataSet ds) { return runOn(ds, null, null); }
    public static Set<OsmPrimitive> runOn(DataSet ds, java.util.Date since) { return runOn(ds, since, null); }
    public static Set<OsmPrimitive> runOn(DataSet ds, java.util.Date since, java.util.Date until) {
        List<Way> buildings=new ArrayList<>(), highways=new ArrayList<>();
        for (Way w : ds.getWays()) { if (w.isClosed()&&w.hasKey("building")&&!"roof".equals(w.get("building"))&&w.getNodesCount()>=4) buildings.add(w); else if (w.hasKey("highway")) highways.add(w); }
        Set<OsmPrimitive> f = new LinkedHashSet<>();
        // NOTE: a shared node does NOT mean "just touching, not crossing" - a building corner
        // accidentally snapped onto a highway node still shares that one node while the rest of
        // the building genuinely straddles the road. highwayCrossesBuilding() already checks every
        // highway node for containment (not just one), so shared nodes are intentionally not
        // special-cased here - see the identical fix in CheckOverlappingBuildingsAction.
        for (Way b : buildings) { if (!GeometryUtil.isMappedDuring(b,since,until)) continue;
            for (Way h : highways) { if (GeometryUtil.bboxDisjoint(b,h)) continue; if (GeometryUtil.highwayCrossesBuilding(h,b)) { f.add(b); break; } }
        }
        return f;
    }
}
