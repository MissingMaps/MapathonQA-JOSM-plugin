package org.openstreetmap.josm.plugins.mapathonqa;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

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
import javax.swing.SwingWorker;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.I18n;

public class RunFullQAAction extends AbstractAction {

    private static final String TM_API = "https://tasking-manager-production-api.hotosm.org/api/v2";

    public RunFullQAAction() { super(I18n.tr("Run Full QA Check...")); }

    @Override
    public void actionPerformed(ActionEvent e) { showStep1Dialog(); }

    private void showStep1Dialog() {
        JDialog dlg = new JDialog((java.awt.Frame) null, "MapathonQA \u2013 Step 1: Project & Time Window", true);
        dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        bindEscapeToClose(dlg);

        JPanel main = new JPanel(new GridBagLayout());
        main.setBorder(BorderFactory.createEmptyBorder(16, 20, 8, 20));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 4, 6, 4); gc.anchor = GridBagConstraints.WEST;

        gc.gridx=0; gc.gridy=0; gc.gridwidth=2;
        main.add(new JLabel("<html><b>Mapathon Name</b> <small>(optional)</small></html>"), gc);
        gc.gridy=1; gc.gridwidth=1;
        gc.gridx=0; main.add(new JLabel("Name:"), gc);
        JTextField mapathonNameField = new JTextField(MapathonQAPlugin.lastMapathonName, 20);
        mapathonNameField.setToolTipText("Shown on the report, e.g. \"Kathmandu University Mapathon\"");
        gc.gridx=1; main.add(mapathonNameField, gc);

        gc.gridx=0; gc.gridy=2; gc.gridwidth=2;
        main.add(new JLabel("<html><b>HOT Tasking Manager Project ID</b></html>"), gc);
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
        main.add(new JLabel("<html><b>Mapathon Time Window (UTC)</b><br><small>Format: YYYY-MM-DD HH:MM</small></html>"), gc);
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

        JPanel btns = new JPanel();
        JButton btnFind = new JButton("Find Mapathon Tasks \u2192");
        JButton btnX    = new JButton("Cancel");
        btns.add(btnFind); btns.add(btnX);

        btnX.addActionListener(ev -> dlg.dispose());
        btnFind.addActionListener(ev -> {
            int pid = parseId(projectIdField.getText());
            if (pid < 1) { JOptionPane.showMessageDialog(dlg, "Please enter a valid project ID.", "MapathonQA", JOptionPane.ERROR_MESSAGE); return; }
            String startVal = startField.getText().trim();
            String endVal   = endField.getText().trim();
            MapathonQAPlugin.lastProjectId    = pid;
            MapathonQAPlugin.lastStart        = startVal;
            MapathonQAPlugin.lastEnd          = endVal;
            MapathonQAPlugin.lastMapathonName = mapathonNameField.getText().trim();
            Config.getPref().putBoolean(HistoryLogger.PREF_INCLUDE_HISTORY, chkHistory.isSelected());
            dlg.dispose();
            fetchTaskIds(pid, startVal, endVal);
        });

        dlg.setLayout(new BorderLayout());
        dlg.add(main, BorderLayout.CENTER);
        dlg.add(btns, BorderLayout.SOUTH);
        dlg.pack();
        dlg.setMinimumSize(dlg.getSize());
        dlg.setLocationRelativeTo(null);
        dlg.setVisible(true);
    }

    private void fetchTaskIds(int projectId, String start, String end) {
        JDialog prog = progressDialog("Connecting to HOT Tasking Manager...");
        JLabel statusLbl = getStatusLabel(prog);
        prog.setVisible(true);

        SwingWorker<List<Integer>, String> worker = new SwingWorker<List<Integer>, String>() {
            @Override protected List<Integer> doInBackground() throws Exception {
                publish("Fetching activity for project #" + projectId + "...");
                List<Integer> ids = fetchMappedTaskIds(projectId, start, end);
                publish("Found " + ids.size() + " task(s). Building search query...");
                return ids;
            }
            @Override protected void process(List<String> chunks) {
                if (!chunks.isEmpty()) statusLbl.setText(chunks.get(chunks.size()-1));
            }
            @Override protected void done() {
                prog.dispose();
                try { showStep2Dialog(projectId, start, end, get()); }
                catch (Exception ex) { JOptionPane.showMessageDialog(null, "Failed to fetch task list:\n"+ex.getMessage(), "MapathonQA", JOptionPane.ERROR_MESSAGE); }
            }
        };
        worker.execute();
    }

    private void showStep2Dialog(int projectId, String start, String end, List<Integer> taskIds) {
        JDialog dlg = new JDialog((java.awt.Frame) null, "MapathonQA \u2013 Step 2: Load & Select Tasks", true);
        dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dlg.setSize(840, 520);
        dlg.setLocationRelativeTo(null);
        bindEscapeToClose(dlg);

        JPanel main = new JPanel(new GridBagLayout());
        main.setBorder(BorderFactory.createEmptyBorder(14, 18, 8, 18));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(5, 4, 5, 4); gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL; gc.weightx = 1.0;

        gc.gridx=0; gc.gridy=0; gc.gridwidth=2;
        String summary = taskIds.isEmpty()
            ? "<html><b style='color:#c62828'>No tasks found</b> mapped between "+start+" and "+end+" UTC.<br>Check the project ID and time window.</html>"
            : "<html><b style='color:#2e7d32'>"+taskIds.size()+" task(s)</b> were mapped during the mapathon<br>(Project #"+projectId+", "+start+" \u2192 "+end+" UTC)</html>";
        main.add(new JLabel(summary), gc);

        String taskGridUrl = "https://tasking-manager-production-api.hotosm.org/api/v2/projects/"+projectId+"/tasks/?as_file=true&format=geojson";
        String searchQuery = taskIds.isEmpty() ? "" : buildJosmSearchQuery(taskIds);
        if (!taskIds.isEmpty()) {
            gc.gridy=1; gc.fill=GridBagConstraints.NONE; gc.weighty=0;
            JButton btnCopyQuery = new JButton("\uD83D\uDCCB Copy Search Query to Clipboard");
            btnCopyQuery.addActionListener(ev -> { Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(searchQuery), null); btnCopyQuery.setText("\u2713 Copied!"); });
            main.add(btnCopyQuery, gc);
        }

        gc.gridy=2; gc.gridwidth=2; gc.fill=GridBagConstraints.HORIZONTAL;
        String steps = taskIds.isEmpty() ? "" :
            "<html><body style='line-height:140%'><b>Next steps:</b><ol style='margin-left:16px'>"
            + "<li style='margin-bottom:8px'>If you left the checkbox below ticked, the task grid will load automatically when you close this dialog.</li>"
            + "<li style='margin-bottom:8px'>Use <b>Edit \u2192 Search (Ctrl+F)</b> and paste the search query you copied above to select the mapathon task squares</li>"
            + "<li style='margin-bottom:8px'>Download OSM data for the selected tasks using the <b>Download Along Way</b> tool</li>"
            + "<li style='margin-bottom:8px'>Click <b>Run QA on Current Layer</b> from the MapathonQA menu</li>"
            + "</ol>"
            + "<p style='margin:6px 0 2px'><b>\u2139 Note on task detection:</b></p>"
            + "<p style='margin:2px 0'>Task IDs are based on the <b>most recent action date</b> per task. Tasks mapped during the mapathon but later re-validated or invalidated may show a different date and could fall outside the window.</p>"
            + "<p style='margin:2px 0'><b>Included task statuses:</b> MAPPED, VALIDATED, INVALIDATED, BADIMAGERY, READY \u2014 all statuses are included, the time window is the only filter.</p>"
            + "</body></html>";
        main.add(new JLabel(steps), gc);

        JCheckBox chkLoad = new JCheckBox("Automatically load task grid into JOSM when closing", true);
        gc.gridy=3;
        if (!taskIds.isEmpty()) main.add(chkLoad, gc);

        JPanel btns = new JPanel();
        JButton btnBack  = new JButton("\u2190 Back");
        JButton btnClose = new JButton("Close & Continue \u2192");
        JButton btnX     = new JButton("Close");
        btns.add(btnBack);
        if (!taskIds.isEmpty()) btns.add(btnClose);
        else btns.add(btnX);

        btnBack.addActionListener(ev -> { dlg.dispose(); showStep1Dialog(); });
        btnX.addActionListener(ev -> dlg.dispose());
        btnClose.addActionListener(ev -> {
            dlg.dispose();
            if (chkLoad.isSelected()) openTaskGridInJosm(taskGridUrl);
        });

        dlg.setLayout(new BorderLayout());
        JScrollPane scroll = new JScrollPane(main); scroll.setBorder(null);
        dlg.add(scroll, BorderLayout.CENTER);
        dlg.add(btns, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }

    /** Lets Esc close a dialog the same way its Cancel/Close button does - Swing doesn't bind this by default. */
    static void bindEscapeToClose(JDialog dlg) {
        dlg.getRootPane().registerKeyboardAction(ev -> dlg.dispose(),
            KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    private List<Integer> fetchMappedTaskIds(int projectId, String startTime, String endTime) throws Exception {
        Set<Integer> ids = new LinkedHashSet<>();
        String tStart = startTime.trim().replace(" ", "T");
        String tEnd   = endTime.trim().replace(" ", "T");
        if (tStart.length() > 16) tStart = tStart.substring(0, 16);
        if (tEnd.length()   > 16) tEnd   = tEnd.substring(0, 16);

        String urlStr = TM_API + "/projects/" + projectId + "/activities/latest/";
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "MapathonQA-JOSMPlugin/1.0");
        conn.setConnectTimeout(15000); conn.setReadTimeout(30000);
        int code = conn.getResponseCode();
        if (code == 403) throw new Exception("Access denied (HTTP 403) to TM API.");
        if (code == 404) throw new Exception("Project #" + projectId + " not found.");
        if (code != 200) throw new Exception("TM API returned HTTP " + code);

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line; while ((line = br.readLine()) != null) sb.append(line);
        }
        String json = sb.toString();
        int activityStart = json.indexOf("\"activity\"");
        if (activityStart < 0) throw new Exception("Unexpected API response format.");
        int arrayStart = json.indexOf("[", activityStart);
        if (arrayStart < 0) throw new Exception("No activity array in response.");

        int pos = arrayStart;
        while (true) {
            int objStart = json.indexOf("{", pos);
            if (objStart < 0) break;
            int objEnd = json.indexOf("}", objStart);
            if (objEnd < 0) break;
            String obj = json.substring(objStart, objEnd + 1);

            int taskId = -1;
            String taskIdStr = extractJsonNumber(obj, "taskId");
            if (taskIdStr != null) { try { taskId = Integer.parseInt(taskIdStr); } catch (NumberFormatException ignored) {} }

            String actionDate = extractJsonString(obj, "actionDate");

            if (taskId > 0 && actionDate != null) {
                String dateShort = actionDate.length() >= 16 ? actionDate.substring(0, 16) : actionDate;
                if (dateShort.compareTo(tStart) >= 0 && dateShort.compareTo(tEnd) <= 0) ids.add(taskId);
            }
            pos = objEnd + 1;
        }
        return new ArrayList<>(ids);
    }

    private String extractJsonString(String json, String key) {
        int idx = json.indexOf("\"" + key + "\""); if (idx < 0) return null;
        int colon = json.indexOf(":", idx); if (colon < 0) return null;
        int q1 = json.indexOf("\"", colon + 1); if (q1 < 0) return null;
        int q2 = json.indexOf("\"", q1 + 1); if (q2 < 0) return null;
        return json.substring(q1 + 1, q2);
    }

    private String extractJsonNumber(String json, String key) {
        int idx = json.indexOf("\"" + key + "\""); if (idx < 0) return null;
        int colon = json.indexOf(":", idx); if (colon < 0) return null;
        int start = colon + 1;
        while (start < json.length() && json.charAt(start) == ' ') start++;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) end++;
        return end > start ? json.substring(start, end) : null;
    }

    private String buildJosmSearchQuery(List<Integer> taskIds) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < taskIds.size(); i++) { if (i > 0) sb.append(" OR "); sb.append("taskId=").append(taskIds.get(i)); }
        return sb.toString();
    }

    private void openTaskGridInJosm(String url) {
        try {
            Class<?> cls = Class.forName("org.openstreetmap.josm.actions.OpenLocationAction");
            Object action = cls.getDeclaredConstructor().newInstance();
            try {
                java.lang.reflect.Method m = cls.getMethod("openUrl", boolean.class, java.util.List.class);
                List<String> urls = new ArrayList<>(); urls.add(url);
                m.invoke(action, false, urls);
            } catch (NoSuchMethodException e1) { try {
                java.lang.reflect.Method m = cls.getMethod("openUrl", java.util.List.class);
                List<String> urls = new ArrayList<>(); urls.add(url);
                m.invoke(action, urls);
            } catch (NoSuchMethodException e2) {
                java.lang.reflect.Method m = cls.getMethod("openUrl", String.class);
                m.invoke(action, url);
            }}
        } catch (Exception ex) { showManualLoadDialog(url); }
    }

    private void showManualLoadDialog(String url) {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.add(new JLabel("<html>Please load the task grid manually:<br><b>File \u2192 Open Location (Ctrl+L)</b> and paste:</html>"), BorderLayout.NORTH);
        JTextArea urlArea = new JTextArea(url);
        urlArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11)); urlArea.setEditable(false); urlArea.setLineWrap(true);
        panel.add(new JScrollPane(urlArea), BorderLayout.CENTER);
        JButton btnCopy = new JButton("Copy URL to Clipboard");
        btnCopy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(url), null));
        panel.add(btnCopy, BorderLayout.SOUTH);
        JOptionPane.showMessageDialog(null, panel, "MapathonQA \u2013 Load Task Grid", JOptionPane.INFORMATION_MESSAGE);
    }

    private int parseId(String text) { try { return Integer.parseInt(text.trim()); } catch (NumberFormatException e) { return -1; } }

    private JDialog progressDialog(String msg) {
        JDialog dlg = new JDialog((java.awt.Frame) null, "MapathonQA \u2013 Please wait...", false);
        dlg.setSize(380, 110); dlg.setLocationRelativeTo(null); dlg.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        JPanel pp = new JPanel(new BorderLayout(10, 10)); pp.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        pp.add(new JLabel(msg), BorderLayout.CENTER);
        JProgressBar bar = new JProgressBar(); bar.setIndeterminate(true); pp.add(bar, BorderLayout.SOUTH);
        dlg.add(pp); return dlg;
    }

    private JLabel getStatusLabel(JDialog dlg) {
        return (JLabel) ((JPanel) dlg.getContentPane().getComponent(0)).getComponent(0);
    }
}
