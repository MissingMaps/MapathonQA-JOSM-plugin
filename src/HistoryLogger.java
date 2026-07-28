package org.openstreetmap.josm.plugins.mapathonqa;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Appends one row per real QA run to a single persistent CSV file, so mapathon
 * organisers can track data quality trends across mapathons over time in
 * Excel/Sheets. Lives in the same folder as HTML reports (see ReportWriter);
 * the same file is reused/appended to on every run, never overwritten.
 * Opt-in via the Step 1 checkbox (see PREF_INCLUDE_HISTORY) - off by default.
 */
public class HistoryLogger {

    /** Whether a real QA run should append a row to the history CSV. Off by default - opt-in via the Step 1 checkbox. */
    public static final String PREF_INCLUDE_HISTORY = "mapathonqa.includeInHistory";

    private static final String FILENAME = "MapathonQA_history.csv";

    private static final String[] HEADER = {
        "Report created (UTC)", "Mapathon Name", "Project ID", "Mapathon Start (UTC)", "Mapathon End (UTC)",
        "Buildings Checked", "Roads Checked", "Mappers", "Issue Mappers",
        "Non-yes Building Tags", "Overlapping Buildings", "Buildings on Roads", "Non-orthogonal Buildings",
        "Buildings with Layer Tag", "Shared Nodes", "Buildings with Shared Nodes", "Untagged Objects",
        "Total Issues", "Objects without Issues", "Quality Score (%)", "Quality Label"
    };

    public static File appendRow(QAResults r) throws IOException {
        File outDir = ReportWriter.resolveOutputDir();
        File file = new File(outDir, FILENAME);
        boolean isNew = !file.exists();

        try (FileOutputStream fos = new FileOutputStream(file, true)) {
            if (isNew) fos.write(new byte[] { (byte) 0xEF, (byte) 0xBB, (byte) 0xBF }); // UTF-8 BOM for Excel
            Writer w = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
            if (isNew) writeRow(w, HEADER);
            writeRow(w, buildRow(r));
            w.flush();
        }
        return file;
    }

    private static String[] buildRow(QAResults r) {
        SimpleDateFormat ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        ts.setTimeZone(TimeZone.getTimeZone("UTC"));
        return new String[] {
            ts.format(new Date()),
            r.mapathonName == null ? "" : r.mapathonName,
            r.projectId > 0 ? String.valueOf(r.projectId) : "",
            r.startTime == null ? "" : r.startTime,
            r.endTime == null ? "" : r.endTime,
            String.valueOf(r.mapathonBuildings),
            String.valueOf(r.mapathonHighways),
            String.valueOf(r.totalMappers),
            String.valueOf(r.issueMappers),
            String.valueOf(r.nonYesBuildingTags.size()),
            String.valueOf(r.overlappingBuildings.size()),
            String.valueOf(r.buildingsOnHighways.size()),
            String.valueOf(r.nonOrthogonalBuildings.size()),
            String.valueOf(r.buildingsWithLayerTag.size()),
            String.valueOf(r.buildingsWithSharedNodes.sharedNodeCount),
            String.valueOf(r.buildingsWithSharedNodes.affectedBuildings.size()),
            String.valueOf(r.untaggedObjects.size()),
            String.valueOf(r.totalIssues()),
            String.valueOf(r.cleanCount()),
            String.format("%.0f", r.qualityScore()),
            r.qualityLabel()
        };
    }

    private static void writeRow(Writer w, String[] values) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(csvEscape(values[i]));
        }
        sb.append("\r\n");
        w.write(sb.toString());
    }

    private static String csvEscape(String s) {
        if (s == null) s = "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
