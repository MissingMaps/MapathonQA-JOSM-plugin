package org.openstreetmap.josm.plugins.mapathonqa;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingWorker;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.I18n;
import org.openstreetmap.josm.tools.OpenBrowser;

public class RunQAOnCurrentLayerAction extends AbstractAction {

    public RunQAOnCurrentLayerAction() {
        super(I18n.tr("Run QA & Generate Report"));
        putValue(SHORT_DESCRIPTION, I18n.tr("Runs the 7 checks against the current layer and generates the PDF "
            + "report. Works on its own, but if you ran ''Find Mapathon Tasks...'' first, results are restricted "
            + "to the mapathon''s time window."));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        runQA(MapathonQAPlugin.lastProjectId,
              MapathonQAPlugin.lastStart,
              MapathonQAPlugin.lastEnd,
              MapathonQAPlugin.lastMapathonName);
    }

    public static void runQA(int projectId, String start, String end, String mapathonName) {
        DataSet ds = MainApplication.getLayerManager().getEditDataSet();
        if (ds == null) {
            JOptionPane.showMessageDialog(null,
                "No active OSM data layer found.\n\n"
                + "Please download OSM data first:\n"
                + "1. Load the task grid into JOSM\n"
                + "2. Use the search query from 'Find Mapathon Tasks...' to select tasks\n"
                + "3. Download OSM data for the selected area\n"
                + "4. Then run this check",
                "MapathonQA", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JDialog prog = new JDialog((java.awt.Frame) null, "MapathonQA \u2013 Running checks...", false);
        prog.setSize(420, 120);
        prog.setLocationRelativeTo(null);
        prog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        JPanel pp = new JPanel(new BorderLayout(10, 10));
        pp.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        String sinceStr = (start != null && !start.isEmpty()) ? " since " + start + " UTC" : "";
        JLabel statusLbl = new JLabel("Starting checks" + sinceStr + "...");
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        pp.add(statusLbl, BorderLayout.CENTER);
        pp.add(bar, BorderLayout.SOUTH);
        prog.add(pp);
        prog.setVisible(true);

        SwingWorker<QAResults, String> worker = new SwingWorker<QAResults, String>() {
            @Override
            protected QAResults doInBackground() throws Exception {
                // FIX 1: parse times as UTC — users enter times in UTC
                // (TM API uses UTC; without this JOSM uses local timezone)
                Date since = parseStartTime(start);
                Date until = parseStartTime(end);

                QAResults r = new QAResults();
                r.projectId     = projectId;
                r.mapathonName  = mapathonName;
                r.startTime  = start;
                r.endTime    = end;
                r.since      = since;
                r.until      = until;
                r.totalNodes     = ds.getNodes().size();
                r.totalWays      = ds.getWays().size();
                r.totalRelations = ds.getRelations().size();
                Set<String> mapperNames = new LinkedHashSet<>();
                for (Way w : ds.getWays()) {
                    if (w.hasKey("building")) {
                        r.totalBuildings++;
                        if (GeometryUtil.isMappedDuring(w, since, until)) r.mapathonBuildings++;
                    }
                    if (w.hasKey("highway")) {
                        r.totalHighways++;
                        if (GeometryUtil.isMappedDuring(w, since, until)) r.mapathonHighways++;
                    }
                    if (GeometryUtil.isMappedDuring(w, since, until) && w.getUser() != null) {
                        mapperNames.add(w.getUser().getName());
                    }
                }
                for (Node n : ds.getNodes()) {
                    if (GeometryUtil.isMappedDuring(n, since, until) && n.getUser() != null) {
                        mapperNames.add(n.getUser().getName());
                    }
                }
                r.totalMappers = mapperNames.size();

                publish("Checking building tags... (1/7)");
                r.nonYesBuildingTags = CheckNonYesBuildingTagsAction.runOn(ds, since, until);

                publish("Checking overlapping buildings... (2/7)");
                r.overlappingBuildings = CheckOverlappingBuildingsAction.runOn(ds, since, until);

                publish("Checking buildings on highways... (3/7)");
                r.buildingsOnHighways = CheckBuildingsOnHighwaysAction.runOn(ds, since, until);

                publish("Checking orthogonality of " + r.totalBuildings + " buildings... (4/7)");
                r.nonOrthogonalBuildings = CheckNonOrthogonalBuildingsAction.runOn(ds, since, until);

                publish("Checking layer tags... (5/7)");
                r.buildingsWithLayerTag = CheckBuildingLayerTagAction.runOn(ds, since, until);

                publish("Checking shared nodes... (6/7)");
                r.buildingsWithSharedNodes = CheckBuildingsWithSharedNodesAction.runOn(ds, since, until);

                publish("Checking untagged objects... (7/7)");
                r.untaggedObjects = CheckUntaggedWaysAction.runOn(ds, since, until);

                Set<String> issueMapperNames = new LinkedHashSet<>();
                for (OsmPrimitive p : r.allFlagged()) {
                    if (p != null && p.getUser() != null) issueMapperNames.add(p.getUser().getName());
                }
                r.issueMappers = issueMapperNames.size();

                return r;
            }

            @Override
            protected void process(List<String> chunks) {
                if (!chunks.isEmpty()) statusLbl.setText(chunks.get(chunks.size() - 1));
            }

            @Override
            protected void done() {
                prog.dispose();
                try {
                    QAResults r = get();

                    if (!r.allFlagged().isEmpty()) ds.setSelected(r.allFlagged());

                    File reportFile = null;
                    try { reportFile = ReportWriter.write(r); }
                    catch (Exception ex) {
                        JOptionPane.showMessageDialog(null,
                            "Could not write the PDF report:\n" + ex.getMessage(),
                            "MapathonQA", JOptionPane.WARNING_MESSAGE);
                    }

                    File historyFile = null;
                    if (Config.getPref().getBoolean(HistoryLogger.PREF_INCLUDE_HISTORY, false)) {
                        try { historyFile = HistoryLogger.appendRow(r); }
                        catch (Exception ex) {
                            JOptionPane.showMessageDialog(null,
                                "Could not update history log:\n" + ex.getMessage(),
                                "MapathonQA", JOptionPane.WARNING_MESSAGE);
                        }
                    }

                    int total = r.totalIssues();
                    String nameInfo = (r.mapathonName != null && !r.mapathonName.trim().isEmpty())
                        ? ReportWriter.esc(r.mapathonName.trim()) + "<br>" : "";
                    String projInfo = projectId > 0
                        ? "Project #" + projectId + "  |  " + ReportWriter.esc(start) + " \u2192 " + ReportWriter.esc(end) + " (UTC)<br>"
                        : "";
                    String summary = MapathonQAPlugin.html(
                        "<div style='font-size:14px; font-weight:bold; margin-bottom:4px;'>Mapathon Quality Report generated</div>"
                        + nameInfo + projInfo
                        + "<br><table cellspacing='0' cellpadding='2' style='margin-left:8px;'>"
                        + tableRow("Non-yes building tags:", r.nonYesBuildingTags.size())
                        + tableRow("Overlapping buildings:", r.overlappingBuildings.size())
                        + tableRow("Buildings on highways:", r.buildingsOnHighways.size())
                        + tableRow("Non-orthogonal buildings:", r.nonOrthogonalBuildings.size())
                        + tableRow("Buildings with layer tag:", r.buildingsWithLayerTag.size())
                        + tableRow("Shared nodes:", r.buildingsWithSharedNodes.sharedNodeCount)
                        + tableRow("Untagged objects:", r.untaggedObjects.size())
                        + "<tr><td colspan='2'><hr></td></tr>"
                        + "<tr><td><b>Total issues:</b></td><td align='right'><b>" + total
                        + " (created by " + r.issueMappers + " mapper" + (r.issueMappers == 1 ? "" : "s") + ")</b></td></tr>"
                        + "</table><br>"
                        + (total > 0 ? "Flagged objects are selected in the editor.<br><br>" : "\u2713 No issues found!<br><br>")
                        + (reportFile != null ? "<b>Report saved to:</b>" : ""));

                    List<Object> message = new ArrayList<>();
                    message.add(new JLabel(summary));
                    if (reportFile != null) {
                        message.add(fileLink(reportFile));
                    }
                    if (historyFile != null) {
                        message.add(new JLabel(MapathonQAPlugin.html("<br><b>History log updated:</b>")));
                        message.add(fileLink(historyFile));
                    }

                    JOptionPane.showMessageDialog(null, message.toArray(), "MapathonQA \u2013 Results",
                        JOptionPane.PLAIN_MESSAGE);

                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(null,
                        "QA check failed:\n" + ex.getMessage(),
                        "MapathonQA", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    /**
     * Parses "YYYY-MM-DD HH:MM" into a Date using UTC timezone.
     * Times are entered by the user as UTC (matching the HOT TM API).
     * Without explicitly setting UTC, Java uses the JVM local timezone
     * which varies by user location and would produce wrong results.
     */
    static Date parseStartTime(String startTime) {
        if (startTime == null || startTime.trim().isEmpty()) return null;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            return sdf.parse(startTime.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String tableRow(String label, int value) {
        return tableRow(label, String.valueOf(value));
    }

    private static String tableRow(String label, String value) {
        return "<tr><td>" + ReportWriter.esc(label) + "</td><td align='right'>&nbsp;&nbsp;" + ReportWriter.esc(value) + "</td></tr>";
    }

    private static JLabel fileLink(File file) {
        JLabel link = new JLabel(MapathonQAPlugin.html("<u>" + ReportWriter.esc(file.getAbsolutePath()) + "</u>"));
        link.setForeground(new Color(0x1a73e8));
        link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        link.setToolTipText("Click to open in the default application");
        link.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 0));
        link.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                OpenBrowser.displayUrl(file.toURI());
            }
        });
        return link;
    }
}
