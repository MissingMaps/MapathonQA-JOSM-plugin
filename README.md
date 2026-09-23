# MapathonQA – JOSM Plugin

Post-mapathon data quality checker. The goal of this plugin is to give a quick, rough overview of the data quality output after a mapathon and create a PDF report that can be shared with mapathon organisers/trainers so they are aware which issues they should highlight next time during training.

The report detects only objects created/modified during the mapathon's time window. "Run QA on Current Layer" uses the time window from the last "Generate Mapathon Report..." run; if there is none, it detects all objects. The Individual Checks submenu detects all objects as well.

Video tutorial: [How to use the MapathonQA plugin](https://youtu.be/k_rTlTLqS7I)

## Workflow

1. **MapathonQA → Generate Mapathon Report...**
   - Enter the mapathon name (optional), the HOT Tasking Manager project ID and the mapathon's UTC time window (defaults to the last 2 hours). Optionally tick "Include this report in MapathonQA_history.csv" to log the results in an Excel file for future tracking and comparing with later mapathons.
   - Click **Generate Report →**. Everything else is automatic:
     1. The plugin queries the HOT TM API for all tasks touched in that time window.
     2. It fetches each task's boundary and downloads the OSM data for every task, one by one, into a new layer ("MapathonQA – *mapathon name*"). **Cancel** (or Esc) in the progress window stops the run; whatever was already downloaded stays in the layer. If some tasks fail to download, the plugin lists their task IDs (selectable, so you can copy them) and asks whether to generate the report from the rest anyway.
     3. It runs the 7 checks against the downloaded data, <ins>restricted to the mapathon's time window</ins>. Flagged objects are selected in the editor, a PDF report is generated, and (if enabled) a row is appended to the history CSV file.
2. Review the flagged selection in JOSM, and share the PDF report with organisers/trainers.

Other entry points from the menu:
- **Run QA on Current Layer** — re-runs the checks and regenerates the report on the active layer without downloading anything (e.g. after fixing some issues, or on data you downloaded yourself).
- **Set Report Save Folder...** — choose where the PDF reports and the history CSV are saved; if unset, falls back to your Downloads folder, then Desktop, then the home folder.
- **Individual Checks submenu** — run any of the 7 report checks standalone against the whole current layer <ins>with no time filter</ins>

The report is a single self-contained PDF — fonts and the Missing Maps logo are embedded, so it renders identically offline on any viewer and previews inline when shared over Slack, email or chat. Two pages at most: page 1 is "how it went" (summary + the seven checks), and when something is flagged the "handy tips" start on page 2. A clean run is one page.

## Credits

The non-orthogonal building check (`CheckNonOrthogonalBuildingsAction.java`) ports the
classification logic from **Mapathoner**'s `Helper.that_building()` — same thresholds, same
branch structure — reimplemented here using JOSM's own `Way.getAngles()`. Mapathoner is by
qeef: https://mapathoner.mapathon.cz/

This plugin was built with the help of Claude, Anthropic's AI chatbot, used throughout for design,
implementation, and debugging.

## Menu structure

```
MapathonQA
├── Generate Mapathon Report...
├── Run QA on Current Layer
├── ───────────────
├── Set Report Save Folder...
├── ───────────────
└── Individual Checks ▸
    ├── Select Non-yes Building Tags
    ├── Select Overlapping Buildings
    ├── Select Buildings on Highways
    ├── Select Non-orthogonal Buildings
    ├── Select Buildings with Layer Tag
    ├── Select Buildings with Shared Nodes
    └── Select Untagged Objects
```

All items in the submenu run with no time filter, independent of the full QA Check/report.

## Architecture

| File | Purpose |
|---|---|
| `MapathonQAPlugin.java` | Entry point, builds menu (see Menu structure below) |
| `GenerateMapathonReportAction.java` | One-click pipeline: HOT TM API → task IDs in the time window → task boundaries → per-task download into a new layer → runs the QA |
| `RunQAOnCurrentLayerAction.java` | Runs all 7 checks with progress dialog, generates the PDF report |
| `SetReportFolderAction.java` | Lets the user override where PDF reports are saved (JOSM preference `mapathonqa.reportDir`) |
| `HistoryLogger.java` | Appends one row per real QA run to a persistent `MapathonQA_history.csv` for tracking quality trends over time — opt-in, off by default |
| `CheckNonYesBuildingTagsAction.java` | Check 1: building ≠ yes (menu: "Select Non-yes Building Tags") |
| `CheckOverlappingBuildingsAction.java` | Check 2: overlapping/contained buildings (matches JOSM's own built-in validator classification — see below), including exact-duplicate ways (`GeometryUtil.isExactDuplicate`) (menu: "Select Overlapping Buildings") |
| `CheckBuildingsOnHighwaysAction.java` | Check 3: buildings crossing roads, excluding `building=roof` (menu: "Select Buildings on Highways") |
| `CheckNonOrthogonalBuildingsAction.java` | Check 4: non-square corners (ported from Mapathoner's `Helper.that_building()`, see Credits) (menu: "Select Non-orthogonal Buildings") |
| `CheckBuildingLayerTagAction.java` | Check 5: buildings with layer=* tag (menu: "Select Buildings with Layer Tag") |
| `CheckBuildingsWithSharedNodesAction.java` | Check 6: shared nodes between buildings and other objects (menu: "Select Buildings with Shared Nodes") |
| `CheckUntaggedWaysAction.java` | Check 7: untagged objects — ways, plus standalone untagged nodes not used as a way vertex (multipolygon members excluded) (menu: "Select Untagged Objects") |
| `GeometryUtil.java` | Ray-casting, segment intersection, exact-duplicate detection, time filter, building-overlap classification via JOSM's own `Geometry.polygonIntersection` |
| `QAResults.java` | Data container for all check results |
| `ReportWriter.java` | Renders the branded PDF report via OpenPDF; extracts a plain `Data` struct from `QAResults`, then draws the card layout with bundled Nunito + Fraunces fonts and the MM logo |

## HOT TM API

```
GET https://tasking-manager-production-api.hotosm.org/api/v2/projects/{ID}/activities/latest/
```

Returns latest action per task. Plugin filters by `actionDate` within the time window.
All taskStatus values included (MAPPED, VALIDATED, INVALIDATED, BADIMAGERY, READY).
Tasks mapped during the mapathon but later re-validated or invalidated may show a later date and fall outside the window.

```
GET https://tasking-manager-production-api.hotosm.org/api/v2/projects/{ID}/tasks/
```

Returns the task grid as GeoJSON. Each matched task's polygon is reduced to its bounding box, and each box is
downloaded from the OSM API separately. There is no buffer; the API still returns ways that cross the box edge complete.

## Building

```
./build.sh      # macOS / Linux
build.bat       # Windows
```

Needs a JDK 17+ on `PATH`. `lib/josm-tested.jar` (gitignored) must be downloaded
from <https://josm.openstreetmap.de/josm-tested.jar>. On the first build, OpenPDF
is fetched into `lib/` from Maven Central and unpacked into `MapathonQA.jar` — JOSM
plugins are self-contained, so the finished jar (~2.4 MB) carries OpenPDF plus the
embedded fonts and logo. If Maven Central is unreachable, drop
`openpdf-1.3.30.jar` into `lib/` by hand.

## Third-party components

| Component | License | Use |
|---|---|---|
| [OpenPDF](https://github.com/LibrePDF/OpenPDF) 1.3.30 | LGPL-2.1 / MPL-2.0 | PDF generation (bundled in the plugin jar) |
| [Nunito](https://github.com/googlefonts/nunito) | SIL OFL 1.1 | Report body text (bundled, Latin subset — see `fonts/README.txt`) |
| [Fraunces](https://github.com/undercasetype/Fraunces) | SIL OFL 1.1 | Report headings and stat numbers (bundled) |

The plugin itself is GPL-3.0 (`LICENSE`); all three are GPL-compatible and are
redistributed unmodified. Font license texts live in `fonts/`.