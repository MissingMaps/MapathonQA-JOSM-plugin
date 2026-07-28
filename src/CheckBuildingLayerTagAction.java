package org.openstreetmap.josm.plugins.mapathonqa;
import java.awt.BorderLayout; import java.awt.event.ActionEvent;
import java.util.*; import javax.swing.*;
import org.openstreetmap.josm.data.osm.*;
import org.openstreetmap.josm.gui.MainApplication; import org.openstreetmap.josm.tools.I18n;
public class CheckBuildingLayerTagAction extends AbstractAction {
    public CheckBuildingLayerTagAction() { super(I18n.tr("Select Buildings with Layer Tag")); }
    @Override public void actionPerformed(ActionEvent e) {
        DataSet ds = MainApplication.getLayerManager().getEditDataSet();
        if (ds == null) { JOptionPane.showMessageDialog(null, "No active OSM data layer found.", "MapathonQA", JOptionPane.WARNING_MESSAGE); return; }
        JDialog prog = CheckNonYesBuildingTagsAction.makeProgress("Checking layer tags...");
        prog.setVisible(true);
        new SwingWorker<List<OsmPrimitive>, Void>() {
            @Override protected List<OsmPrimitive> doInBackground() { return runOn(ds, null, null); }
            @Override protected void done() {
                prog.dispose();
                try {
                    List<OsmPrimitive> f = get();
                    if (f.isEmpty()) { JOptionPane.showMessageDialog(null, "No buildings with a layer tag found.", "MapathonQA", JOptionPane.INFORMATION_MESSAGE); return; }
                    ds.setSelected(f);
                } catch (Exception ex) { prog.dispose(); JOptionPane.showMessageDialog(null, "Check failed:\n"+ex.getMessage(), "MapathonQA", JOptionPane.ERROR_MESSAGE); }
            }
        }.execute();
    }
    public static List<OsmPrimitive> runOn(DataSet ds) { return runOn(ds, null, null); }
    public static List<OsmPrimitive> runOn(DataSet ds, java.util.Date since) { return runOn(ds, since, null); }
    public static List<OsmPrimitive> runOn(DataSet ds, java.util.Date since, java.util.Date until) {
        List<OsmPrimitive> f = new ArrayList<>();
        for (Way w : ds.getWays()) { if (!GeometryUtil.isMappedDuring(w,since,until)) continue; if (w.hasKey("building")&&w.hasKey("layer")) f.add(w); }
        for (Relation r : ds.getRelations()) { if (!GeometryUtil.isMappedDuring(r,since,until)) continue; if (r.hasKey("building")&&r.hasKey("layer")) f.add(r); }
        return f;
    }
}
