package org.openstreetmap.josm.plugins.mapathonqa;
import java.awt.BorderLayout; import java.awt.event.ActionEvent;
import java.util.*; import javax.swing.*;
import org.openstreetmap.josm.data.osm.*;
import org.openstreetmap.josm.gui.MainApplication; import org.openstreetmap.josm.tools.I18n;
import org.openstreetmap.josm.tools.Pair;
/**
 * Classification logic ported from Mapathoner's Helper.that_building() (Mapathoner by qeef,
 * https://mapathoner.mapathon.cz/): same thresholds and branch structure, driven by JOSM's own
 * Way.getAngles() (projected EastNorth corner angles) instead of a hand-rolled lat/lon calc,
 * and with no node-count cap, matching Mapathoner's implementation.
 */
public class CheckNonOrthogonalBuildingsAction extends AbstractAction {
    private static final double SQ_TH=1.0, RD_TH=1.0;
    public CheckNonOrthogonalBuildingsAction() { super(I18n.tr("Select Non-orthogonal Buildings")); }
    @Override public void actionPerformed(ActionEvent e) {
        DataSet ds = MainApplication.getLayerManager().getEditDataSet();
        if (ds == null) { JOptionPane.showMessageDialog(null, "No active OSM data layer found.", "MapathonQA", JOptionPane.WARNING_MESSAGE); return; }
        JDialog prog = CheckNonYesBuildingTagsAction.makeProgress("Checking building orthogonality...");
        prog.setVisible(true);
        new SwingWorker<List<OsmPrimitive>, Void>() {
            @Override protected List<OsmPrimitive> doInBackground() { return runOn(ds, null, null); }
            @Override protected void done() {
                prog.dispose();
                try {
                    List<OsmPrimitive> f = get();
                    if (f.isEmpty()) { JOptionPane.showMessageDialog(null, "No non-orthogonal buildings found.", "MapathonQA", JOptionPane.INFORMATION_MESSAGE); return; }
                    ds.setSelected(f);
                } catch (Exception ex) { prog.dispose(); JOptionPane.showMessageDialog(null, "Check failed:\n"+ex.getMessage(), "MapathonQA", JOptionPane.ERROR_MESSAGE); }
            }
        }.execute();
    }
    public static List<OsmPrimitive> runOn(DataSet ds) { return runOn(ds, null, null); }
    public static List<OsmPrimitive> runOn(DataSet ds, java.util.Date since) { return runOn(ds, since, null); }
    public static List<OsmPrimitive> runOn(DataSet ds, java.util.Date since, java.util.Date until) {
        List<OsmPrimitive> f = new ArrayList<>();
        for (Way w : ds.getWays()) {
            if (!w.isClosed()||!w.hasKey("building")) continue;
            if (!GeometryUtil.isMappedDuring(w,since,until)) continue;
            int nc=w.getNodesCount()-1; if (nc<3) continue;
            if (classifyBuilding(w,nc,SQ_TH,RD_TH)==4) f.add(w);
        }
        return f;
    }
    static int classifyBuilding(Way w, int n, double sqTh, double rdTh) {
        double ep=180.0-360.0/n;
        double ssd=0; int iSq=0,iRd=0,nz=0,mSq=0,mRd=0; final double M=15.0;
        for (Pair<Double,Node> pair : w.getAngles()) {
            double ang=pair.a;
            double sd=Math.min(Math.abs(90.0-ang),Math.abs(180.0-ang)); ssd+=sd;
            if (sd<sqTh) iSq++; else if (sd<M) mSq++;
            double rd=Math.abs(ep-ang); if (rd<rdTh) iRd++; else if (rd<M) mRd++;
            if (Math.abs(ang)<sqTh) nz++;
        }
        ssd=ssd%90.0;
        if (iSq==n) return 2; if (iRd==n&&n>4) return 1;
        if (iRd+mRd>iSq+mSq) return mRd>0?3:0;
        if (mSq>0) return 4; if (ssd<sqTh&&nz==0) return 2; return 4;
    }
}
