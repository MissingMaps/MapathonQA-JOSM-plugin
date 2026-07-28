package org.openstreetmap.josm.plugins.mapathonqa;

import java.awt.event.ActionEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.TimeZone;

import javax.swing.AbstractAction;
import javax.swing.JOptionPane;

import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.tools.I18n;

/**
 * Generates a demo HTML report with realistic simulated issue counts
 * across all 7 checks. Useful for showing colleagues what a full report
 * looks like without needing real OSM data.
 */
public class GenerateDemoReportAction extends AbstractAction {

    public GenerateDemoReportAction() {
        super(I18n.tr("Generate Demo Report"));
    }

    /**
     * A Set that always reports a fixed size, regardless of contents.
     * Used for demo purposes to show 100 issues in Set-typed check results
     * without deduplication collapsing multiple nulls into one.
     */
    private static class FixedSizeSet extends AbstractSet<OsmPrimitive> {
        private final int fixedSize;
        FixedSizeSet(int size) { this.fixedSize = size; }
        @Override public int size() { return fixedSize; }
        @Override public boolean isEmpty() { return fixedSize == 0; }
        @Override public Iterator<OsmPrimitive> iterator() { return new java.util.ArrayList<OsmPrimitive>().iterator(); }
        @Override public boolean add(OsmPrimitive e) { return false; }
        @Override public boolean addAll(Collection<? extends OsmPrimitive> c) { return false; }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        QAResults r = new QAResults();
        r.projectId = 12345;
        r.mapathonName = "Sample City Mapathon";
        r.startTime = "2026-01-01 09:00";
        r.endTime   = "2026-01-01 13:00";

        // Set since/until so "last edited during mapathon" counts appear
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            r.since = sdf.parse("2026-01-01 09:00");
            r.until = sdf.parse("2026-01-01 13:00");
        } catch (Exception ex) { /* ignore */ }

        r.totalBuildings  = 620;
        r.totalHighways   = 180;
        r.totalNodes      = 18340;
        r.totalWays       = 2589;
        r.totalRelations  = 12;
        r.totalIssueOverride = 84;
        r.mapathonBuildings = 520;
        r.mapathonHighways  = 150;
        r.totalMappers = 47;
        r.issueMappers = 3;

        // Varied per-check counts so every row in the table looks realistic.
        // These intentionally sum to more than totalIssueOverride (84) - a
        // building can be flagged by more than one check, so the deduplicated
        // total is lower than the sum of the individual category counts.
        for (int i = 0; i < 22; i++) r.nonYesBuildingTags.add(null);
        for (int i = 0; i < 31; i++) r.nonOrthogonalBuildings.add(null);
        for (int i = 0; i < 9;  i++) r.buildingsWithLayerTag.add(null);
        for (int i = 0; i < 15; i++) r.untaggedObjects.add(null);

        // Sets deduplicate nulls -> use FixedSizeSet to report a fixed count
        // Overlapping buildings: 14 flagged, 3 of them exact duplicates
        r.overlappingBuildings = new CheckOverlappingBuildingsAction.OverlapResult(new FixedSizeSet(14), 3, 14);
        r.buildingsOnHighways  = new FixedSizeSet(6);

        // Shared nodes: 12 shared nodes across 8 affected buildings
        r.buildingsWithSharedNodes = new CheckBuildingsWithSharedNodesAction.SharedNodeResult(
            12, new FixedSizeSet(8)
        );

        try {
            File report = ReportWriter.writeDemoReport(r);
            JOptionPane.showMessageDialog(null,
                "Demo report saved to:\n" + report.getAbsolutePath()
                + "\n\nThis is a demonstration report with simulated issue counts.\n"
                + "Not based on real OSM data.",
                "MapathonQA \u2013 Demo Report",
                JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null,
                "Could not write demo report:\n" + ex.getMessage(),
                "MapathonQA", JOptionPane.ERROR_MESSAGE);
        }
    }
}
