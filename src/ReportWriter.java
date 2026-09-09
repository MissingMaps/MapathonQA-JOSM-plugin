package org.openstreetmap.josm.plugins.mapathonqa;

import java.awt.Color;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import org.openstreetmap.josm.spi.preferences.Config;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPCellEvent;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;

/**
 * Generates the branded MapathonQA quality report as a single self-contained PDF
 * (OpenPDF). Fonts (Nunito + Fraunces, SIL OFL) and the Missing Maps logo are
 * bundled in the plugin jar under /fonts and /images and embedded into the file,
 * so the report renders identically offline on any viewer.
 *
 * Layout mirrors the previous HTML report: a white card system on a warm ground,
 * a meta strip, two summary stat cards, the seven-check issues table, and a
 * "handy tips" section. The report divides on a conceptual seam - page 1 is
 * "how it went", the tips start on a fresh page when they would not fit.
 */
public class ReportWriter {

    public static final String PREF_REPORT_DIR = "mapathonqa.reportDir";

    // ---- palette (sRGB approximations of the old report's oklch values) ----
    private static final Color CREAM     = new Color(0xF7, 0xF4, 0xEF);
    private static final Color WHITE     = Color.WHITE;
    private static final Color INK       = new Color(0x40, 0x3D, 0x38);
    private static final Color INK_HEAD  = new Color(0x41, 0x36, 0x2C);
    private static final Color MUTE      = new Color(0x82, 0x7B, 0x71);
    private static final Color MUTE_SOFT = new Color(0x6D, 0x66, 0x5D);
    private static final Color HAIR      = new Color(0xE7, 0xE1, 0xD7);
    private static final Color GREEN     = new Color(0x3C, 0x89, 0x5E);
    private static final Color GREEN_DK  = new Color(0x2C, 0x79, 0x50);
    private static final Color GREEN_BAR = new Color(0x5A, 0xAC, 0x7B);
    private static final Color AMBER     = new Color(0xB9, 0x71, 0x28);
    private static final Color AMBER_BAR = new Color(0xDC, 0x99, 0x42);
    private static final Color AMBER_PILL= new Color(0xF5, 0xE8, 0xD6);
    private static final Color PILL_BG   = new Color(0xDB, 0xE6, 0xF0);
    private static final Color PILL_TX   = new Color(0x31, 0x56, 0x7C);

    private static BaseFont N_REG, N_SEMI, N_BOLD, N_XB, F_HEAD, F_NUM;

    // =====================================================================
    //  Public entry point
    // =====================================================================

    public static File write(QAResults r) throws IOException {
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String projectPart = r.projectId > 0 ? "project" + r.projectId : "standalone";
        String filename = "MapathonQA_" + projectPart + "_" + ts + ".pdf";
        File out = new File(resolveOutputDir(), filename);

        try (OutputStream os = new BufferedOutputStream(new FileOutputStream(out))) {
            render(extract(r), os);
        } catch (DocumentException e) {
            throw new IOException("Could not build the PDF report: " + e.getMessage(), e);
        }
        return out;
    }

    // =====================================================================
    //  Data extraction (keeps rendering free of JOSM types, and testable)
    // =====================================================================

    static final class Data {
        String mapathonName;
        int projectId;
        String startTime, endTime;
        int totalMappers, issueMappers;
        int mapathonFeatures, totalIssues, clean, cleanPct, issuesPct;
        int nonYes, overlap, duplicates, onRoads, nonOrtho, layerTag, sharedNodes, sharedBldgs, untagged;
        String generatedUtc;
    }

    static Data extract(QAResults r) {
        Data d = new Data();
        d.mapathonName    = r.mapathonName;
        d.projectId       = r.projectId;
        d.startTime       = r.startTime;
        d.endTime         = r.endTime;
        d.totalMappers    = r.totalMappers;
        d.issueMappers    = r.issueMappers;
        d.mapathonFeatures = r.mapathonFeatures();
        d.totalIssues     = r.totalIssues();
        d.clean           = r.cleanCount();
        d.cleanPct        = d.mapathonFeatures > 0 ? Math.round(100f * d.clean / d.mapathonFeatures) : 100;
        d.issuesPct       = d.mapathonFeatures > 0 ? Math.round(100f * d.totalIssues / d.mapathonFeatures) : 0;
        d.nonYes          = r.nonYesBuildingTags.size();
        d.overlap         = r.overlappingBuildings.size();
        d.duplicates      = r.overlappingBuildings.duplicateBuildingCount;
        d.onRoads         = r.buildingsOnHighways.size();
        d.nonOrtho        = r.nonOrthogonalBuildings.size();
        d.layerTag        = r.buildingsWithLayerTag.size();
        d.sharedNodes     = r.buildingsWithSharedNodes.sharedNodeCount;
        d.sharedBldgs     = r.buildingsWithSharedNodes.affectedBuildings.size();
        d.untagged        = r.untaggedObjects.size();

        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        d.generatedUtc = f.format(new Date());
        return d;
    }

    // =====================================================================
    //  Rendering
    // =====================================================================

    static void render(Data d, OutputStream out) throws IOException, DocumentException {
        loadFonts();

        Document doc = new Document(PageSize.A4, 44, 44, 44, 58);
        PdfWriter writer = PdfWriter.getInstance(doc, out);
        writer.setPageEvent(new Chrome(d.generatedUtc));
        doc.open();

        float cw = doc.getPageSize().getWidth() - 88f;
        int total = d.totalIssues;
        Image tick = checkMark(writer);

        // ---- header ----
        PdfPTable hIn = new PdfPTable(new float[]{ 70f, cw - 70f - 14f });
        hIn.setWidthPercentage(100);
        Image logo = image("/images/MM_logo_circle.png");
        logo.scaleToFit(60f, 60f);
        PdfPCell lc = new PdfPCell(logo, false);
        lc.setBorder(Rectangle.NO_BORDER);
        lc.setHorizontalAlignment(Element.ALIGN_LEFT);
        lc.setVerticalAlignment(Element.ALIGN_MIDDLE);
        hIn.addCell(lc);
        PdfPCell ht = new PdfPCell();
        ht.setBorder(Rectangle.NO_BORDER);
        ht.setVerticalAlignment(Element.ALIGN_MIDDLE);
        ht.setPaddingLeft(2f);
        Paragraph h1 = new Paragraph("Thank you for organising a mapathon!", f(F_HEAD, 17.5f, INK_HEAD));
        h1.setLeading(20f);
        Paragraph h2 = new Paragraph(
                "Here’s some friendly feedback on how it went, and a few things to watch for next time.",
                f(N_REG, 9.6f, MUTE));
        h2.setLeading(13f);
        h2.setSpacingBefore(3.5f);
        ht.addElement(h1);
        ht.addElement(h2);
        hIn.addCell(ht);
        doc.add(wrapCard(cw, hIn, 18f, 15f, 8f));

        // ---- meta: only the fields that have a value, like the old report ----
        List<PdfPCell> metaCells = new ArrayList<>();
        List<Float> metaW = new ArrayList<>();

        if (notBlank(d.mapathonName)) {
            metaCells.add(metaCell("MAPATHON", new Phrase(d.mapathonName.trim(), f(N_REG, 10.5f, INK))));
            metaW.add(1.7f);
        }
        if (d.projectId > 0) {
            Chunk pj = new Chunk("#" + d.projectId, f(N_SEMI, 10.5f, GREEN));
            pj.setAnchor("https://tasks.hotosm.org/projects/" + d.projectId);
            metaCells.add(metaCell("PROJECT", new Phrase(pj)));
            metaW.add(0.7f);
        }
        if (notBlank(d.startTime)) {
            metaCells.add(metaCell("WHEN", whenPhrase(d.startTime, d.endTime, f(N_REG, 10.5f, INK))));
            metaW.add(1.95f);
        }
        Chunk pill = new Chunk(d.totalMappers + (d.totalMappers == 1 ? " contributor" : " contributors"),
                f(N_BOLD, 9f, PILL_TX));
        pill.setBackground(PILL_BG, 5f, 3f, 5f, 4f);
        Paragraph pillP = new Paragraph(pill);
        pillP.setLeading(15f);
        metaCells.add(metaCell("MAPPERS", pillP));
        metaW.add(1.0f);

        // when only a couple of fields are shown, a trailing spacer keeps them
        // packed to the left instead of stretched across the whole card
        if (metaCells.size() < 4) {
            metaCells.add(new PdfPCell());
            metaCells.get(metaCells.size() - 1).setBorder(Rectangle.NO_BORDER);
            metaW.add(Math.max(1.2f, (4 - metaW.size()) * 1.4f));
        }

        float[] w = new float[metaW.size()];
        for (int i = 0; i < w.length; i++) w[i] = metaW.get(i);
        PdfPTable meta = new PdfPTable(w.length);
        meta.setWidthPercentage(100);
        meta.setWidths(w);
        for (PdfPCell c : metaCells) meta.addCell(c);
        doc.add(wrapCard(cw, meta, 16f, 12f, 10f));

        // ---- summary strip ----
        PdfPTable strip = new PdfPTable(2);
        strip.setWidthPercentage(100);
        strip.setWidths(new float[]{ 1f, 1f });

        String cleanMain = nf(d.clean) + (d.clean == 1 ? " object came" : " objects came")
                + " out clean — great mapping!";
        strip.addCell(summaryCell(d.cleanPct, GREEN_DK, cleanMain, null, null, null, GREEN_BAR, 0f, 6.5f));

        String issueMain = total == 0
                ? "nothing flagged by the automated checks"
                : nf(total) + (total == 1 ? " object has" : " objects have") + " a little room to grow";
        String issueSub = total > 0
                ? "from " + d.issueMappers + (d.issueMappers == 1 ? " mapper" : " mappers")
                : null;
        strip.addCell(summaryCell(d.issuesPct, AMBER, issueMain, issueSub, AMBER_PILL, AMBER, AMBER_BAR, 6.5f, 0f));
        strip.setSpacingAfter(10f);
        doc.add(strip);

        // ---- issues table ----
        float[] tblCols = { 1.95f, 0.8f, 2.85f };

        PdfPTable body = new PdfPTable(1);
        body.setWidthPercentage(100);
        body.addCell(sectionHead("A few things worth a second look",
                "Nothing alarming here — just small tweaks that’ll make the map even better."));

        // The green "CHECK / ISSUES / NOTES" bar, drawn as an image so it renders
        // reliably at this nesting depth (cell backgrounds and events on a deeply
        // nested table do not paint in OpenPDF).
        PdfPCell barRow = new PdfPCell(headerBar(writer, cw - 34f, tblCols), false);
        barRow.setBorder(Rectangle.NO_BORDER);
        barRow.setPadding(0f);
        barRow.setPaddingTop(12f);
        barRow.setPaddingBottom(3f);
        body.addCell(barRow);

        PdfPTable tbl = new PdfPTable(tblCols);
        tbl.setWidthPercentage(100);

        String overlapNote = "Buildings that geometrically overlap or are contained within another building "
                + "(each count = one pair).";
        if (d.duplicates > 0) overlapNote += " " + d.duplicates + " building(s) were duplicated.";

        row(tbl, tick, "Buildings tagging", d.nonYes,
            "Buildings tagged differently than building=yes.", false);
        row(tbl, tick, "Overlapping buildings", d.overlap, overlapNote, false);
        row(tbl, tick, "Building outlines that cross a highway", d.onRoads,
            "Building drawn through an existing highway.", false);
        row(tbl, tick, "Non-orthogonal buildings", d.nonOrtho,
            "Rectangular buildings that most likely should be orthogonal with squared corners.", false);
        row(tbl, tick, "Buildings with layer tag", d.layerTag,
            "Buildings tagged with layer=* created as recommendation from iD editor when two objects are "
            + "overlapping. The correct solution is for the objects to not overlap.", false);
        row(tbl, tick, "Buildings with shared nodes", d.sharedNodes,
            "Buildings sharing at least one node with another object (each count = one shared node, not a "
            + "pair; " + d.sharedBldgs + " building(s) affected).", false);
        row(tbl, tick, "Untagged objects", d.untagged,
            "Nodes and ways with no tags, most likely mappers forgot to add a tag such as building=yes.", true);

        PdfPCell tblWrap = new PdfPCell(tbl);
        tblWrap.setBorder(Rectangle.NO_BORDER);
        tblWrap.setPadding(0f);
        body.addCell(tblWrap);

        if (total == 0) {
            Paragraph allClear = new Paragraph("Every check passed — keep up the great mapping!",
                    f(N_BOLD, 9.5f, GREEN_DK));
            allClear.setAlignment(Element.ALIGN_CENTER);
            allClear.setSpacingBefore(13f);
            PdfPCell ac = new PdfPCell();
            ac.setBorder(Rectangle.NO_BORDER);
            ac.setPadding(0f);
            ac.addElement(allClear);
            body.addCell(ac);
        }
        doc.add(wrapCard(cw, body, 17f, 15f, 9f));

        // ---- recommendations ----
        // Only when something was flagged - a clean run doesn't need tips, and the
        // 100% card plus the line above already say "great mapping". When there are
        // tips, they travel as one block: page 1 if they fit, else a fresh page.
        if (total > 0) {
            doc.add(tipsBlock(cw, tipsFor(d)));
        }

        doc.close();
    }

    // ---- tips: same set and wording as the old report (only rendered when total > 0) ----
    static String[][] tipsFor(Data d) {
        List<String[]> t = new ArrayList<>();
        if (d.nonYes > 0) t.add(new String[]{ "Use building=yes for all buildings",
            "Unless the project instructions say otherwise or you have local knowledge of the area you are mapping." });
        if (d.overlap > 0) t.add(new String[]{ "Don’t draw a new building overlapping an already existing one",
            "Try to draw each building separately. Zoom in and look for outlines already drawn in the area "
            + "before tracing a new one." });
        if (d.onRoads > 0) t.add(new String[]{ "Do not draw buildings over highways",
            "Building outlines should sit beside highways, not on top of them." });
        if (d.nonOrtho > 0) t.add(new String[]{ "Square building corners after drawing",
            "Press “Q” in your mapping editor after drawing a rectangular building outline to straighten "
            + "the corners. If mapping in JOSM, use the buildings_tools plugin which draws rectangular buildings "
            + "automatically." });
        if (d.layerTag > 0) t.add(new String[]{ "Avoid using the layer tag on buildings",
            "When iD editor warns about overlapping objects it suggests adding layer=*. The correct fix is to "
            + "move the object instead so it does not overlap, not to add a layer tag." });
        if (d.sharedNodes > 0) t.add(new String[]{ "Do not snap buildings to highways or other buildings",
            "Each building should have its own independent nodes. In iD editor hold “Alt” (“Ctrl” "
            + "in JOSM) to avoid snapping to existing nodes. If you accidentally connected nodes, use “D” in "
            + "iD editor (“G” in JOSM) to unglue them and then adjust their position." });
        if (d.untagged > 0) t.add(new String[]{ "Always add tags to the nodes and ways you draw",
            "A node or way with no tags has no meaning in OpenStreetMap. If you drew a building outline, make sure "
            + "to add building=yes before saving; if you placed a standalone node, tag it appropriately." });
        return t.toArray(new String[0][]);
    }

    // =====================================================================
    //  Building blocks
    // =====================================================================

    private static PdfPTable wrapCard(float width, PdfPTable inner, float padH, float padV, float spacingAfter) {
        PdfPTable t = new PdfPTable(1);
        t.setTotalWidth(width);
        t.setLockedWidth(true);
        t.setKeepTogether(true);
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        c.setPaddingLeft(padH);
        c.setPaddingRight(padH);
        c.setPaddingTop(padV);
        c.setPaddingBottom(padV);
        c.setCellEvent(new RoundedBox(WHITE, HAIR, 9f, 0f, 0f, 0, null));
        c.addElement(inner);
        t.addCell(c);
        t.setSpacingAfter(spacingAfter);
        return t;
    }

    private static PdfPCell metaCell(String label, Phrase value) {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        c.setPaddingTop(1f);
        c.setPaddingBottom(1f);
        c.setPaddingRight(9f);
        Chunk lc = new Chunk(label, f(N_XB, 7.3f, MUTE));
        lc.setCharacterSpacing(1.1f);
        Paragraph l = new Paragraph(lc);
        l.setLeading(10f);
        Paragraph v = new Paragraph(value);
        v.setLeading(13f);
        v.setSpacingBefore(3f);
        c.addElement(l);
        c.addElement(v);
        return c;
    }

    private static PdfPCell summaryCell(int pct, Color numColor, String label, String sub,
                                        Color pillBg, Color pillTx, Color accent, float insetL, float insetR) {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        c.setPaddingLeft(17f + insetL);
        c.setPaddingRight(17f + insetR);
        c.setPaddingTop(15f);
        c.setPaddingBottom(13f);
        c.setCellEvent(new RoundedBox(WHITE, HAIR, 9f, insetL, insetR, 1, accent));

        Phrase big = new Phrase();
        big.add(new Chunk(String.valueOf(pct), f(F_NUM, 30f, numColor)));
        big.add(new Chunk("%", f(N_BOLD, 14.5f, numColor)));
        if (sub != null) {
            big.add(new Chunk("   ", f(N_REG, 8.4f, numColor)));
            Chunk s = new Chunk(sub, f(N_BOLD, 8.4f, pillTx));
            s.setBackground(pillBg, 5f, 2.5f, 5f, 3.5f);
            s.setTextRise(6f);
            big.add(s);
        }
        Paragraph b = new Paragraph(big);
        b.setLeading(34f);

        Paragraph l = new Paragraph(label, f(N_REG, 9.8f, MUTE_SOFT));
        l.setLeading(13f);
        l.setSpacingBefore(6f);
        c.addElement(b);
        c.addElement(l);
        return c;
    }

    private static PdfPCell sectionHead(String title, String lede) {
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        c.setPadding(0f);
        Paragraph t = new Paragraph(title, f(F_HEAD, 14f, INK_HEAD));
        t.setLeading(16.5f);
        Paragraph l = new Paragraph(lede, f(N_REG, 9.5f, MUTE));
        l.setLeading(12.5f);
        l.setSpacingBefore(3f);
        c.addElement(t);
        c.addElement(l);
        return c;
    }

    private static void row(PdfPTable t, Image tick, String check, int count, String note, boolean last) {
        PdfPCell c1 = new PdfPCell(new Phrase(check, f(N_BOLD, 9.4f, INK)));
        Phrase issues;
        if (count == 0) {
            issues = new Phrase();
            issues.add(new Chunk(tick, 0f, -1.5f, false));
            issues.add(new Chunk("  None", f(N_BOLD, 9.4f, GREEN_DK)));
        } else {
            issues = new Phrase(nf(count), f(N_BOLD, 9.6f, AMBER));
        }
        PdfPCell c2 = new PdfPCell(issues);
        Paragraph np = new Paragraph(note, f(N_REG, 8.6f, MUTE));
        np.setLeading(11.4f);
        PdfPCell c3 = new PdfPCell();
        c3.addElement(np);
        for (PdfPCell c : new PdfPCell[]{ c1, c2, c3 }) {
            c.setBackgroundColor(WHITE);
            c.setBorder(last ? Rectangle.NO_BORDER : Rectangle.BOTTOM);
            c.setBorderColorBottom(HAIR);
            c.setBorderWidthBottom(0.7f);
            c.setPaddingTop(8f);
            c.setPaddingBottom(8f);
            c.setPaddingLeft(9f);
            c.setPaddingRight(6f);
            c.setVerticalAlignment(Element.ALIGN_TOP);
        }
        t.addCell(c1);
        t.addCell(c2);
        t.addCell(c3);
    }

    private static PdfPTable recItem(float width, String title, String body) {
        PdfPTable t = new PdfPTable(1);
        t.setTotalWidth(width);
        t.setLockedWidth(true);
        t.setKeepTogether(true);
        t.setSpacingAfter(6f);
        PdfPCell c = new PdfPCell();
        c.setBorder(Rectangle.NO_BORDER);
        c.setPaddingLeft(16f);
        c.setPaddingRight(15f);
        c.setPaddingTop(10f);
        c.setPaddingBottom(body == null ? 10f : 11f);
        c.setCellEvent(new RoundedBox(WHITE, HAIR, 8f, 0f, 0f, 2, GREEN));
        Paragraph tt = new Paragraph(title, f(N_BOLD, 10f, INK_HEAD));
        tt.setLeading(13f);
        c.addElement(tt);
        if (body != null) {
            Paragraph b = new Paragraph(body, f(N_REG, 8.9f, MUTE_SOFT));
            b.setLeading(12.5f);
            b.setSpacingBefore(2.5f);
            c.addElement(b);
        }
        t.addCell(c);
        return t;
    }

    /**
     * The heading and every tip as one keep-together unit, so the section never
     * splits mid-way and the heading is never orphaned from its tips: it stays on
     * page 1 if it fits, otherwise the whole block moves to a fresh page.
     */
    private static PdfPTable tipsBlock(float width, String[][] tips) {
        PdfPTable outer = new PdfPTable(1);
        outer.setTotalWidth(width);
        outer.setLockedWidth(true);
        outer.setKeepTogether(true);
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0f);

        PdfPTable head = new PdfPTable(1);
        head.setWidthPercentage(100);
        head.addCell(sectionHead("Handy tips for your next mapathon",
                "Quick reminders to make next time even smoother — you’ve already got the hang of it."));
        cell.addElement(wrapCard(width, head, 17f, 13f, 8f));

        for (String[] tip : tips) cell.addElement(recItem(width, tip[0], tip[1]));
        outer.addCell(cell);
        return outer;
    }

    // =====================================================================
    //  Helpers
    // =====================================================================

    private static Font f(BaseFont bf, float size, Color c) {
        return new Font(bf, size, Font.NORMAL, c);
    }

    private static String nf(int n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    /**
     * The "WHEN" value. Same day -&gt; one compact line ("2026-09-02, 10:00-12:00 UTC").
     * Different days -&gt; two lines so the narrow column never wraps mid-timestamp:
     * "2026-09-01 16:00 -" / "2026-09-03 18:00 UTC".
     */
    private static Phrase whenPhrase(String start, String end, Font font) {
        start = start.trim();
        end = end == null ? "" : end.trim();
        if (end.isEmpty()) {
            return new Phrase(start + " UTC", font);
        }
        if (start.length() >= 16 && end.length() >= 16 && start.substring(0, 10).equals(end.substring(0, 10))) {
            return new Phrase(start.substring(0, 10) + ", "
                    + start.substring(11, 16) + "–" + end.substring(11, 16) + " UTC", font);
        }
        Phrase p = new Phrase();
        p.add(new Chunk(start + " –", font));
        p.add(Chunk.NEWLINE);
        p.add(new Chunk(end + " UTC", font));
        return p;
    }

    private static synchronized void loadFonts() throws IOException, DocumentException {
        if (N_REG != null) return;
        N_REG  = loadFont("Nunito-Regular.ttf");
        N_SEMI = loadFont("Nunito-SemiBold.ttf");
        N_BOLD = loadFont("Nunito-Bold.ttf");
        N_XB   = loadFont("Nunito-ExtraBold.ttf");
        F_HEAD = loadFont("Fraunces-Head.ttf");
        F_NUM  = loadFont("Fraunces-Num.ttf");
    }

    /** The green "CHECK / ISSUES / NOTES" bar as an image (see call site for why). */
    private static Image headerBar(PdfWriter writer, float width, float[] cols) throws DocumentException {
        float h = 21f;
        PdfTemplate tp = writer.getDirectContent().createTemplate(width, h);
        tp.setColorFill(GREEN);
        tp.roundRectangle(0f, 0f, width, h, 6.5f);
        tp.fill();

        float span = cols[0] + cols[1] + cols[2];
        float x0 = 9f;
        float x1 = width * cols[0] / span + 9f;
        float x2 = width * (cols[0] + cols[1]) / span + 9f;
        float y = (h - 7.8f) / 2f + 0.5f;

        tp.beginText();
        tp.setFontAndSize(N_XB, 7.8f);
        tp.setColorFill(WHITE);
        tp.setCharacterSpacing(1.1f);
        tp.setTextMatrix(x0, y); tp.showText("CHECK");
        tp.setTextMatrix(x1, y); tp.showText("ISSUES");
        tp.setTextMatrix(x2, y); tp.showText("NOTES");
        tp.endText();

        return Image.getInstance(tp);
    }

    /** A small green check drawn as vector - no symbol font to depend on. */
    private static Image checkMark(PdfWriter writer) throws DocumentException {
        PdfTemplate t = writer.getDirectContent().createTemplate(9f, 8f);
        t.setColorStroke(GREEN_DK);
        t.setLineWidth(1.35f);
        t.setLineCap(PdfContentByte.LINE_CAP_ROUND);
        t.setLineJoin(PdfContentByte.LINE_JOIN_ROUND);
        t.moveTo(0.8f, 4.1f);
        t.lineTo(3.3f, 1.4f);
        t.lineTo(8.2f, 7.4f);
        t.stroke();
        Image img = Image.getInstance(t);
        img.scaleToFit(9f, 8f);
        return img;
    }

    private static BaseFont loadFont(String name) throws IOException, DocumentException {
        byte[] bytes = resourceBytes("/fonts/" + name);
        return BaseFont.createFont(name, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, bytes, null);
    }

    private static Image image(String path) throws IOException {
        try {
            return Image.getInstance(resourceBytes(path));
        } catch (com.lowagie.text.BadElementException e) {
            throw new IOException("Bad bundled image " + path + ": " + e.getMessage(), e);
        }
    }

    private static byte[] resourceBytes(String path) throws IOException {
        try (InputStream in = ReportWriter.class.getResourceAsStream(path)) {
            if (in == null) throw new IOException("Bundled resource not found: " + path);
            return in.readAllBytes();
        }
    }

    static File resolveOutputDir() {
        String configured = Config.getPref().get(PREF_REPORT_DIR, "");
        if (!configured.isEmpty()) {
            File dir = new File(configured);
            if (dir.isDirectory()) return dir;
        }
        File outDir = new File(System.getProperty("user.home"), "Downloads");
        if (!outDir.exists()) outDir = new File(System.getProperty("user.home"), "Desktop");
        if (!outDir.exists()) outDir = new File(System.getProperty("user.home"));
        return outDir;
    }

    /** HTML-escape - still used by the Swing result dialogs in RunQAOnCurrentLayerAction. */
    static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    // =====================================================================
    //  PDF canvas pieces
    // =====================================================================

    /** Rounded-rectangle cell background, with an optional top (1) or left (2) accent bar. */
    private static final class RoundedBox implements PdfPCellEvent {
        private final Color fill, stroke, accent;
        private final float radius, insetL, insetR;
        private final int accentSide;

        RoundedBox(Color fill, Color stroke, float radius, float insetL, float insetR, int accentSide, Color accent) {
            this.fill = fill;
            this.stroke = stroke;
            this.radius = radius;
            this.insetL = insetL;
            this.insetR = insetR;
            this.accentSide = accentSide;
            this.accent = accent;
        }

        @Override
        public void cellLayout(PdfPCell cell, Rectangle pos, PdfContentByte[] cv) {
            PdfContentByte cb = cv[PdfPTable.BACKGROUNDCANVAS];
            float x = pos.getLeft() + insetL;
            float y = pos.getBottom();
            float w = pos.getWidth() - insetL - insetR;
            float h = pos.getHeight();
            cb.saveState();

            cb.setColorFill(fill);
            cb.roundRectangle(x, y, w, h, radius);
            cb.fill();

            if (accent != null && accentSide != 0) {
                cb.saveState();
                cb.roundRectangle(x, y, w, h, radius);
                cb.clip();
                cb.newPath();
                cb.setColorFill(accent);
                if (accentSide == 1) {
                    cb.rectangle(x, y + h - 4f, w, 4f);
                } else {
                    cb.rectangle(x, y, 3.6f, h);
                }
                cb.fill();
                cb.restoreState();
            }

            if (stroke != null) {
                cb.setColorStroke(stroke);
                cb.setLineWidth(0.75f);
                cb.roundRectangle(x, y, w, h, radius);
                cb.stroke();
            }
            cb.restoreState();
        }
    }

    /** Paints the warm page ground and the centred footer on every page. */
    private static final class Chrome extends PdfPageEventHelper {
        private final String generated;

        Chrome(String generated) {
            this.generated = generated;
        }

        @Override
        public void onStartPage(PdfWriter writer, Document doc) {
            PdfContentByte cb = writer.getDirectContentUnder();
            Rectangle ps = doc.getPageSize();
            cb.saveState();
            cb.setColorFill(CREAM);
            cb.rectangle(0, 0, ps.getWidth(), ps.getHeight());
            cb.fill();
            cb.restoreState();
        }

        @Override
        public void onEndPage(PdfWriter writer, Document doc) {
            PdfContentByte cb = writer.getDirectContent();
            Rectangle ps = doc.getPageSize();
            float cx = ps.getWidth() / 2f;

            cb.saveState();
            cb.setColorStroke(HAIR);
            cb.setLineWidth(0.75f);
            cb.moveTo(cx - 80f, 42f);
            cb.lineTo(cx + 80f, 42f);
            cb.stroke();
            cb.restoreState();

            Phrase p1 = new Phrase();
            p1.add(new Chunk("With thanks from ", f(N_REG, 8.3f, MUTE)));
            Chunk mm = new Chunk("Missing Maps", f(N_BOLD, 8.3f, GREEN));
            mm.setAnchor("https://www.missingmaps.org");
            p1.add(mm);
            p1.add(new Chunk("  —  keep mapping!", f(N_REG, 8.3f, MUTE)));
            ColumnText.showTextAligned(cb, Element.ALIGN_CENTER, p1, cx, 31f, 0);
            ColumnText.showTextAligned(cb, Element.ALIGN_CENTER,
                    new Phrase("Generated " + generated + " (UTC)", f(N_REG, 7.5f, new Color(0x9A, 0x93, 0x88))),
                    cx, 21f, 0);
        }
    }
}
