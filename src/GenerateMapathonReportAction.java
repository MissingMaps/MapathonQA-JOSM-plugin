package org.openstreetmap.josm.plugins.mapathonqa;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonValue;

import org.openstreetmap.josm.actions.downloadtasks.DownloadOsmTask;
import org.openstreetmap.josm.actions.downloadtasks.DownloadParams;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.progress.NullProgressMonitor;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.I18n;

/**
 * One-click pipeline: given a HOT Tasking Manager project and the mapathon's time window, finds
 * the tasks touched during that window, downloads the OSM data for each task into a new layer
 * automatically (same DownloadOsmTask/DownloadParams API josm-batch-downloader uses to
 * batch-download a set of bounding boxes), then runs the QA checks and generates the report - no
 * manual search/select/download steps.
 */
public class GenerateMapathonReportAction extends AbstractAction {

    private static final String TM_API = "https://tasking-manager-production-api.hotosm.org/api/v2";

    public GenerateMapathonReportAction() {
        super(I18n.tr("Generate Mapathon Report..."));
        putValue(SHORT_DESCRIPTION, I18n.tr("Finds the tasks mapped during the mapathon''s time window in a HOT "
            + "Tasking Manager project, downloads the OSM data for them automatically, then runs the QA checks "
            + "and generates the PDF report."));
    }

    @Override
    public void actionPerformed(ActionEvent e) { showStep1Dialog(); }

    private void showStep1Dialog() {
        JDialog dlg = new JDialog((java.awt.Frame) null, "MapathonQA \u2013 Project & Time Window", true);
        dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        bindEscapeToClose(dlg);

        JPanel main = new JPanel(new GridBagLayout());
        main.setBorder(BorderFactory.createEmptyBorder(16, 20, 8, 20));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 4, 6, 4); gc.anchor = GridBagConstraints.WEST;

        gc.gridx=0; gc.gridy=0; gc.gridwidth=2;
        main.add(new JLabel(MapathonQAPlugin.html("<b>Mapathon Name</b> <small>(optional)</small>")), gc);
        gc.gridy=1; gc.gridwidth=1;
        gc.gridx=0; main.add(new JLabel("Name:"), gc);
        JTextField mapathonNameField = new JTextField(MapathonQAPlugin.lastMapathonName, 20);
        mapathonNameField.setToolTipText("Shown on the report, e.g. \"Kathmandu University Mapathon\"");
        gc.gridx=1; main.add(mapathonNameField, gc);

        gc.gridx=0; gc.gridy=2; gc.gridwidth=2;
        main.add(new JLabel(MapathonQAPlugin.html("<b>HOT Tasking Manager Project ID</b>")), gc);
        gc.gridy=3; gc.gridwidth=1;
        gc.gridx=0; main.add(new JLabel("Project ID:"), gc);
        JTextField projectIdField = new JTextField(
            MapathonQAPlugin.lastProjectId > 0 ? String.valueOf(MapathonQAPlugin.lastProjectId) : "", 10);
        projectIdField.setToolTipText("HOT Tasking Manager project number, e.g. 50430");
        gc.gridx=1; main.add(projectIdField, gc);

        // Compute default time window: end = current UTC hour (floored), start = end - 2h
        java.util.Calendar cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        String defaultEnd = sdf.format(cal.getTime());
        cal.add(java.util.Calendar.HOUR_OF_DAY, -2);
        String defaultStart = sdf.format(cal.getTime());
        String initialStart = !MapathonQAPlugin.lastStart.isEmpty() ? MapathonQAPlugin.lastStart : defaultStart;
        String initialEnd   = !MapathonQAPlugin.lastEnd.isEmpty()   ? MapathonQAPlugin.lastEnd   : defaultEnd;

        gc.gridx=0; gc.gridy=4; gc.gridwidth=2;
        main.add(new JLabel(MapathonQAPlugin.html("<b>Mapathon Time Window (UTC)</b><br><small>Format: YYYY-MM-DD HH:MM</small>")), gc);
        gc.gridy=5; gc.gridwidth=1;
        gc.gridx=0; main.add(new JLabel("Start (UTC):"), gc);
        JTextField startField = new JTextField(initialStart, 16);
        gc.gridx=1; main.add(startField, gc);
        gc.gridy=6;
        gc.gridx=0; main.add(new JLabel("End (UTC):"), gc);
        JTextField endField = new JTextField(initialEnd, 16);
        gc.gridx=1; main.add(endField, gc);

        gc.gridx=0; gc.gridy=7; gc.gridwidth=2; gc.insets = new Insets(14, 4, 6, 4);
        JCheckBox chkHistory = new JCheckBox("Include this report in MapathonQA_history.csv",
            Config.getPref().getBoolean(HistoryLogger.PREF_INCLUDE_HISTORY, false));
        chkHistory.setToolTipText("Appends one row to a persistent history CSV for tracking quality trends across mapathons over time");
        main.add(chkHistory, gc);

        gc.gridx=0; gc.gridy=8; gc.gridwidth=2; gc.insets = new Insets(10, 4, 6, 4);
        gc.fill = GridBagConstraints.HORIZONTAL;
        // Plain JTextArea, not an HTML JLabel: Swing's HTML view computes its wrap width once at
        // creation and doesn't reliably recompute it inside a layout manager, so long text gets
        // squished until the user manually resizes the dialog. JTextArea re-wraps to whatever
        // width it is actually given, so it renders correctly from the very first paint.
        JTextArea note = new JTextArea(
            "Downloads the OSM data for every task mapped during this time window into a new layer "
            + "automatically, then runs the QA checks and generates the report \u2014 no further steps needed.", 2, 44);
        note.setEditable(false);
        note.setFocusable(false);
        note.setOpaque(false);
        note.setLineWrap(true);
        note.setWrapStyleWord(true);
        note.setFont(mapathonNameField.getFont().deriveFont(11f));
        note.setForeground(new java.awt.Color(0x70, 0x70, 0x70));
        main.add(note, gc);

        JPanel btns = new JPanel();
        JButton btnGo = new JButton("Generate Report \u2192");
        JButton btnX  = new JButton("Cancel");
        btns.add(btnGo); btns.add(btnX);

        btnX.addActionListener(ev -> dlg.dispose());
        btnGo.addActionListener(ev -> {
            int pid = parseId(projectIdField.getText());
            if (pid < 1) { JOptionPane.showMessageDialog(dlg, "Please enter a valid project ID.", "MapathonQA", JOptionPane.ERROR_MESSAGE); return; }
            String startVal = startField.getText().trim();
            String endVal   = endField.getText().trim();
            if (RunQAOnCurrentLayerAction.parseStartTime(startVal) == null || RunQAOnCurrentLayerAction.parseStartTime(endVal) == null) {
                JOptionPane.showMessageDialog(dlg, "Please enter the start and end time as YYYY-MM-DD HH:MM (UTC).", "MapathonQA", JOptionPane.ERROR_MESSAGE);
                return;
            }
            MapathonQAPlugin.lastProjectId    = pid;
            MapathonQAPlugin.lastStart        = startVal;
            MapathonQAPlugin.lastEnd          = endVal;
            MapathonQAPlugin.lastMapathonName = mapathonNameField.getText().trim();
            Config.getPref().putBoolean(HistoryLogger.PREF_INCLUDE_HISTORY, chkHistory.isSelected());
            dlg.dispose();
            runPipeline(pid, startVal, endVal, MapathonQAPlugin.lastMapathonName);
        });

        dlg.setLayout(new BorderLayout());
        dlg.add(main, BorderLayout.CENTER);
        dlg.add(btns, BorderLayout.SOUTH);
        dlg.setSize(520, 460);
        dlg.setLocationRelativeTo(null);
        dlg.setVisible(true);
    }

    // =====================================================================
    //  Pipeline: find tasks -> fetch boundaries -> download -> run QA
    // =====================================================================

    private void runPipeline(int projectId, String start, String end, String mapathonName) {
        RunState state = new RunState();
        JDialog prog = progressDialog("Connecting to HOT Tasking Manager...", state);
        JLabel statusLbl = getStatusLabel(prog);

        SwingWorker<TaskPlan, String> worker = new SwingWorker<TaskPlan, String>() {
            @Override protected TaskPlan doInBackground() throws Exception {
                publish("Fetching activity for project #" + projectId + "...");
                List<Integer> ids = fetchMappedTaskIds(projectId, start, end);
                if (ids.isEmpty()) return new TaskPlan(ids, new ArrayList<>());
                publish("Found " + ids.size() + " task(s). Fetching task boundaries...");
                return new TaskPlan(ids, fetchTaskAreas(projectId, ids));
            }
            @Override protected void process(List<String> chunks) {
                if (!chunks.isEmpty()) statusLbl.setText(chunks.get(chunks.size() - 1));
            }
            @Override protected void done() {
                if (state.cancelled) { prog.dispose(); return; }
                try {
                    TaskPlan plan = get();
                    if (plan.ids.isEmpty()) {
                        prog.dispose();
                        JOptionPane.showMessageDialog(null,
                            "No tasks found in project #" + projectId + " mapped between "
                            + start + " and " + end + " UTC.\n\nCheck the project ID and time window.",
                            "MapathonQA", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    if (plan.areas.isEmpty()) {
                        prog.dispose();
                        JOptionPane.showMessageDialog(null,
                            "Found " + plan.ids.size() + " task(s), but could not read their boundaries "
                            + "from the Tasking Manager.",
                            "MapathonQA", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    // Tasks with no readable boundary can't be downloaded - report them with the failed downloads.
                    Set<Integer> withArea = new HashSet<>();
                    for (TaskArea a : plan.areas) withArea.add(a.taskId);
                    for (Integer id : plan.ids) if (!withArea.contains(id)) state.failedTaskIds.add(id);

                    String layerName = (mapathonName != null && !mapathonName.isEmpty())
                        ? "MapathonQA \u2013 " + mapathonName
                        : "MapathonQA \u2013 Project #" + projectId;
                    OsmDataLayer layer = new OsmDataLayer(new DataSet(), layerName, null);
                    MainApplication.getLayerManager().addLayer(layer);
                    MainApplication.getLayerManager().setActiveLayer(layer);
                    downloadNext(prog, statusLbl, state, plan.areas, 0, plan.ids.size(),
                        () -> RunQAOnCurrentLayerAction.runQA(projectId, start, end, mapathonName));
                } catch (Exception ex) {
                    prog.dispose();
                    JOptionPane.showMessageDialog(null, "Failed to fetch task list:\n" + ex.getMessage(), "MapathonQA", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        state.fetchWorker = worker;
        worker.execute();
        prog.setVisible(true);
    }

    /** Downloads each task's bounds one at a time (via MainApplication.worker, so they never overlap in flight). */
    private void downloadNext(JDialog prog, JLabel statusLbl, RunState state, List<TaskArea> areas, int index,
                              int totalTasks, Runnable onAllDone) {
        if (state.cancelled) {
            prog.dispose();
            JOptionPane.showMessageDialog(null,
                "Download cancelled after " + index + " of " + areas.size() + " task(s).\n\n"
                + "The data downloaded so far is kept in the new layer - use 'Run QA on Current Layer' "
                + "if you want to check it anyway.",
                "MapathonQA", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (index >= areas.size()) {
            prog.dispose();
            if (state.failedTaskIds.isEmpty() || confirmContinueWithFailures(state.failedTaskIds, totalTasks)) {
                onAllDone.run();
            }
            return;
        }
        TaskArea area = areas.get(index);
        String failedInfo = state.failedTaskIds.isEmpty() ? "" : " (" + state.failedTaskIds.size() + " failed)";
        statusLbl.setText("Downloading task " + (index + 1) + " of " + areas.size() + failedInfo + "...");
        DownloadOsmTask task = new DownloadOsmTask();
        state.currentDownload = task;
        // A null monitor makes JOSM fall back to its own PleaseWaitProgressMonitor dialog, which
        // pops up and grabs focus/brings the main window forward for every single download - with
        // one task per download that means constant focus-stealing. NullProgressMonitor has no UI
        // at all, so the background downloads stay silent and our own progress dialog is the only
        // thing shown.
        Future<?> future = task.download(new DownloadParams(), area.bounds, NullProgressMonitor.INSTANCE);
        MainApplication.worker.submit(() -> {
            boolean failed;
            try { future.get(); failed = task.isFailed(); }
            catch (Exception ex) { failed = true; }
            boolean countAsFailed = failed && !task.isCanceled() && !state.cancelled;
            SwingUtilities.invokeLater(() -> {
                state.currentDownload = null;
                if (countAsFailed) state.failedTaskIds.add(area.taskId);
                downloadNext(prog, statusLbl, state, areas, index + 1, totalTasks, onAllDone);
            });
        });
    }

    /**
     * Lists the tasks that couldn't be downloaded and asks whether to generate the report from the
     * rest anyway. The IDs sit in a selectable text area so they can be copied into the Tasking
     * Manager or a manual download. Returns false (no report) when nothing at all was downloaded.
     */
    private static boolean confirmContinueWithFailures(List<Integer> failedTaskIds, int totalTasks) {
        List<Integer> sorted = new ArrayList<>(failedTaskIds);
        java.util.Collections.sort(sorted);
        StringBuilder ids = new StringBuilder();
        for (Integer id : sorted) { if (ids.length() > 0) ids.append(", "); ids.append(id); }

        JTextArea idArea = new JTextArea(ids.toString(), Math.min(4, 1 + sorted.size() / 10), 36);
        idArea.setEditable(false);
        idArea.setLineWrap(true);
        idArea.setWrapStyleWord(true);
        JScrollPane idScroll = new JScrollPane(idArea);

        boolean nothingDownloaded = failedTaskIds.size() >= totalTasks;
        String header = "Could not download " + failedTaskIds.size() + " of " + totalTasks + " task(s). Task IDs:";
        if (nothingDownloaded) {
            JOptionPane.showMessageDialog(null,
                new Object[] { header, idScroll, " ", "No data was downloaded, so no report was generated. "
                    + "Check your connection and try again." },
                "MapathonQA \u2013 Download failed", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        int choice = JOptionPane.showConfirmDialog(null,
            new Object[] { header, idScroll, " ",
                "Generate the report anyway? Objects in these tasks won't be checked." },
            "MapathonQA \u2013 Some downloads failed", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        return choice == JOptionPane.YES_OPTION;
    }

    /** Lets Esc close a dialog the same way its Cancel/Close button does - Swing doesn't bind this by default. */
    static void bindEscapeToClose(JDialog dlg) {
        dlg.getRootPane().registerKeyboardAction(ev -> dlg.dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    // =====================================================================
    //  HOT Tasking Manager API
    // =====================================================================

    /**
     * Task IDs whose most recent action falls inside the time window. All statuses count - the
     * time window is the only filter.
     */
    private List<Integer> fetchMappedTaskIds(int projectId, String startTime, String endTime) throws Exception {
        String tStart = startTime.trim().replace(" ", "T");
        String tEnd   = endTime.trim().replace(" ", "T");
        if (tStart.length() > 16) tStart = tStart.substring(0, 16);
        if (tEnd.length()   > 16) tEnd   = tEnd.substring(0, 16);

        JsonObject root = fetchJson(TM_API + "/projects/" + projectId + "/activities/latest/");
        JsonArray activity = root.getJsonArray("activity");
        if (activity == null) throw new Exception("Unexpected API response format.");

        Set<Integer> ids = new LinkedHashSet<>();
        for (JsonValue v : activity) {
            try {
                JsonObject obj = v.asJsonObject();
                int taskId = obj.getInt("taskId", -1);
                String actionDate = obj.isNull("actionDate") ? null : obj.getString("actionDate", null);
                if (taskId > 0 && actionDate != null) {
                    String dateShort = actionDate.length() >= 16 ? actionDate.substring(0, 16) : actionDate;
                    if (dateShort.compareTo(tStart) >= 0 && dateShort.compareTo(tEnd) <= 0) ids.add(taskId);
                }
            } catch (Exception ignored) {
                // malformed entry - skip it rather than aborting the whole fetch
            }
        }
        return new ArrayList<>(ids);
    }

    /** Fetches every requested task's polygon from the project's task GeoJSON and reduces it to a bbox. */
    private List<TaskArea> fetchTaskAreas(int projectId, List<Integer> taskIds) throws Exception {
        Set<Integer> wanted = new HashSet<>(taskIds);
        Map<Integer, double[]> byId = new HashMap<>();

        JsonObject root = fetchJson(TM_API + "/projects/" + projectId + "/tasks/");
        JsonArray features = root.getJsonArray("features");
        if (features != null) {
            for (JsonValue fv : features) {
                try {
                    JsonObject feature = fv.asJsonObject();
                    JsonObject props = feature.getJsonObject("properties");
                    if (props == null) continue;
                    int taskId = props.getInt("taskId", -1);
                    if (!wanted.contains(taskId)) continue;
                    JsonObject geom = feature.getJsonObject("geometry");
                    if (geom == null) continue;
                    JsonArray coords = geom.getJsonArray("coordinates");
                    if (coords == null) continue;
                    double[] bbox = { Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                                       Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY };
                    walkCoords(coords, bbox);
                    if (bbox[0] <= bbox[2] && bbox[1] <= bbox[3]) byId.put(taskId, bbox);
                } catch (Exception ignored) {
                    // malformed feature - skip it
                }
            }
        }

        List<TaskArea> result = new ArrayList<>();
        for (Integer id : taskIds) {
            double[] bbox = byId.get(id);
            if (bbox != null) result.add(new TaskArea(id, boundsOf(bbox)));
        }
        return result;
    }

    /** Walks an arbitrarily nested GeoJSON coordinates array (Polygon or MultiPolygon) tracking the [lon,lat] extent. */
    private static void walkCoords(JsonValue v, double[] bbox) {
        if (v.getValueType() != JsonValue.ValueType.ARRAY) return;
        JsonArray arr = v.asJsonArray();
        if (arr.size() >= 2
                && arr.get(0).getValueType() == JsonValue.ValueType.NUMBER
                && arr.get(1).getValueType() == JsonValue.ValueType.NUMBER) {
            double lon = ((JsonNumber) arr.get(0)).doubleValue();
            double lat = ((JsonNumber) arr.get(1)).doubleValue();
            if (lon < bbox[0]) bbox[0] = lon;
            if (lat < bbox[1]) bbox[1] = lat;
            if (lon > bbox[2]) bbox[2] = lon;
            if (lat > bbox[3]) bbox[3] = lat;
            return;
        }
        for (JsonValue child : arr) walkCoords(child, bbox);
    }

    /**
     * No buffer: the OSM API's bbox download already returns a way complete as long as one of its
     * nodes falls inside the box (it fetches the out-of-box nodes too), so a building straddling a
     * task's edge isn't actually at risk of being clipped. Padding the box would only pull in
     * unrelated neighboring data.
     */
    private static Bounds boundsOf(double[] bbox) {
        double minLon = bbox[0], minLat = bbox[1], maxLon = bbox[2], maxLat = bbox[3];
        return new Bounds(minLat, minLon, maxLat, maxLon);
    }

    private JsonObject fetchJson(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "MapathonQA-JOSMPlugin/1.0");
        conn.setConnectTimeout(15000); conn.setReadTimeout(30000);
        int code = conn.getResponseCode();
        if (code == 403) throw new Exception("Access denied (HTTP 403) to TM API.");
        if (code == 404) throw new Exception("Not found (HTTP 404): " + urlStr);
        if (code != 200) throw new Exception("TM API returned HTTP " + code);
        try (InputStream in = conn.getInputStream(); JsonReader reader = Json.createReader(in)) {
            return reader.readObject();
        }
    }

    private static final class TaskArea {
        final int taskId;
        final Bounds bounds;
        TaskArea(int taskId, Bounds bounds) { this.taskId = taskId; this.bounds = bounds; }
    }

    private static final class TaskPlan {
        final List<Integer> ids;
        final List<TaskArea> areas;
        TaskPlan(List<Integer> ids, List<TaskArea> areas) { this.ids = ids; this.areas = areas; }
    }

    /** Shared state of one pipeline run, so the progress dialog's Cancel button can stop whichever phase is running. */
    private static final class RunState {
        volatile boolean cancelled;
        SwingWorker<?, ?> fetchWorker;
        DownloadOsmTask currentDownload;
        final List<Integer> failedTaskIds = new ArrayList<>();

        void cancel() {
            cancelled = true;
            if (fetchWorker != null) fetchWorker.cancel(true);
            DownloadOsmTask download = currentDownload;
            if (download != null) download.cancel();
        }
    }

    // =====================================================================
    //  Small UI helpers
    // =====================================================================

    private int parseId(String text) { try { return Integer.parseInt(text.trim()); } catch (NumberFormatException e) { return -1; } }

    private JDialog progressDialog(String msg, RunState state) {
        // Owned by the main frame (not a bare null owner) so it minimizes/restores together with
        // it, the same as JOSM's own progress dialogs.
        JDialog dlg = new JDialog(MainApplication.getMainFrame(), "MapathonQA \u2013 Please wait...", false);
        dlg.setSize(400, 150); dlg.setLocationRelativeTo(null); dlg.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        JPanel pp = new JPanel(new BorderLayout(10, 10)); pp.setBorder(BorderFactory.createEmptyBorder(16, 20, 8, 20));
        JLabel statusLbl = new JLabel(msg);
        pp.add(statusLbl, BorderLayout.CENTER);
        JProgressBar bar = new JProgressBar(); bar.setIndeterminate(true); pp.add(bar, BorderLayout.SOUTH);

        JButton btnCancel = new JButton("Cancel");
        Runnable cancel = () -> {
            if (state.cancelled) return;
            btnCancel.setEnabled(false);
            statusLbl.setText("Cancelling...");
            state.cancel();
        };
        btnCancel.addActionListener(ev -> cancel.run());
        // The window's close button and Esc cancel too, instead of hiding the dialog while downloads keep running.
        dlg.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { cancel.run(); }
        });
        dlg.getRootPane().registerKeyboardAction(ev -> cancel.run(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        JPanel btns = new JPanel();
        btns.add(btnCancel);

        dlg.setLayout(new BorderLayout());
        dlg.add(pp, BorderLayout.CENTER);
        dlg.add(btns, BorderLayout.SOUTH);
        return dlg;
    }

    private JLabel getStatusLabel(JDialog dlg) {
        return (JLabel) ((JPanel) dlg.getContentPane().getComponent(0)).getComponent(0);
    }
}
