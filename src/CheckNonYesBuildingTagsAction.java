package org.openstreetmap.josm.plugins.mapathonqa;
import java.awt.BorderLayout; import java.awt.event.ActionEvent;
import java.util.*; import javax.swing.*;
import org.openstreetmap.josm.data.osm.*;
import org.openstreetmap.josm.gui.MainApplication; import org.openstreetmap.josm.tools.I18n;
public class CheckNonYesBuildingTagsAction extends AbstractAction {
    public CheckNonYesBuildingTagsAction() { super(I18n.tr("Select Non-yes Building Tags")); }
    @Override public void actionPerformed(ActionEvent e) {
        DataSet ds = MainApplication.getLayerManager().getEditDataSet();
        if (ds == null) { JOptionPane.showMessageDialog(null, "No active OSM data layer found.", "MapathonQA", JOptionPane.WARNING_MESSAGE); return; }
        JDialog prog = makeProgress("Checking building tags...");
        prog.setVisible(true);
        new SwingWorker<List<OsmPrimitive>, Void>() {
            @Override protected List<OsmPrimitive> doInBackground() { return runOn(ds, null, null); }
            @Override protected void done() {
                prog.dispose();
                try {
                    List<OsmPrimitive> f = get();
                    if (f.isEmpty()) { JOptionPane.showMessageDialog(null, "No non-yes building tags found. All buildings use building=yes.", "MapathonQA", JOptionPane.INFORMATION_MESSAGE); return; }
                    ds.setSelected(f);
                } catch (Exception ex) { prog.dispose(); JOptionPane.showMessageDialog(null, "Check failed:\n"+ex.getMessage(), "MapathonQA", JOptionPane.ERROR_MESSAGE); }
            }
        }.execute();
    }
    public static List<OsmPrimitive> runOn(DataSet ds) { return runOn(ds, null, null); }
    public static List<OsmPrimitive> runOn(DataSet ds, java.util.Date since) { return runOn(ds, since, null); }
    public static List<OsmPrimitive> runOn(DataSet ds, java.util.Date since, java.util.Date until) {
        List<OsmPrimitive> f = new ArrayList<>();
        for (Way w : ds.getWays()) { if (!GeometryUtil.isMappedDuring(w,since,until)) continue; String v=w.get("building"); if (v!=null&&!v.equals("yes")) f.add(w); }
        for (Relation r : ds.getRelations()) { if (!GeometryUtil.isMappedDuring(r,since,until)) continue; String v=r.get("building"); if (v!=null&&!v.equals("yes")) f.add(r); }
        return f;
    }
    static JDialog makeProgress(String msg) {
        JDialog d = new JDialog((java.awt.Frame)null, "MapathonQA \u2013 Checking...", false);
        d.setSize(360,100); d.setLocationRelativeTo(null); d.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        JPanel p = new JPanel(new BorderLayout(10,10)); p.setBorder(javax.swing.BorderFactory.createEmptyBorder(16,20,16,20));
        p.add(new JLabel(msg), BorderLayout.CENTER);
        JProgressBar bar = new JProgressBar(); bar.setIndeterminate(true); p.add(bar, BorderLayout.SOUTH);
        d.add(p); return d;
    }
}
