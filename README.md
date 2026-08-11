# MapathonQA – JOSM Plugin

Post-mapathon data quality checker. The goal of this plugin is to give a quick, rough overview of the data quality output after a mapathon and create a report that can be shared with mapathon organisers/trainers so they are aware which issues they should highlight next time during training. 

"Run QA on Current Layer" detects only objects created/modified during the mapathon's time window, as defined in "Run Full QA Check". The Individual Checks submenu detects all objects.

## Workflow

1. **MapathonQA → Run Full QA Check...**
   - **Step 1 – Project & Time Window:** enter the mapathon name (optional), the HOT Tasking Manager project ID and the mapathon's UTC time window (defaults to the last 2 hours). Optionally tick "Include this report in MapathonQA_history.csv" to log the results in an Excel file for future tracking and comparing with later mapathons.
   - Click **Find Mapathon Tasks →**. The plugin queries the HOT TM API for all tasks touched in that time window and builds a JOSM search query matching them.
   - **Step 2 – Load & Select Tasks:** Click "Copy Search Query to Clipboard".
2. Load the task grid into JOSM — happens automatically on **Close & Continue** if you leave the checkbox ticked.
3. **Edit → Search (Ctrl+F)**, paste the copied search query to select the tasks touched during the mapathon's time window
4. Download OSM data for the selected tasks using File → **Download Along...**
5. **MapathonQA → Run QA on Current Layer** — runs the 7 checks against the downloaded data, <ins>restricted to the mapathon's time window</ins>. Flagged objects are selected in the editor, an HTML report is generated, and (if enabled in Step 1) a row is appended to the history CSV file.
6. Review the flagged selection in JOSM, and share the HTML report with organisers/trainers.

Other entry points from the menu:
- **Generate Demo Report** — produces a sample HTML report with simulated issue counts, for previewing the report format without running it against real data.
- **Set Report Save Folder...** — choose where reports and the history CSV are saved; if unset, falls back to your Downloads folder, then Desktop, then the home folder.
- **Individual Checks submenu** — run any of the 7 report checks standalone against the whole current layer <ins>with no time filter</ins>
- **3rdPass Checks (Not in Report) submenu** — extra checks for HOT TM Third Pass Validation; not part of the QA report.

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
├── Run Full QA Check...
├── Run QA on Current Layer
├── ───────────────
├── Generate Demo Report
├── Set Report Save Folder...
├── ───────────────
├── Individual Checks ▸
│   ├── Select Non-yes Building Tags
│   ├── Select Overlapping Buildings
│   ├── Select Buildings on Highways
│   ├── Select Non-orthogonal Buildings
│   ├── Select Buildings with Layer Tag
│   ├── Select Buildings with Shared Nodes
│   └── Select Untagged Objects
└── 3rdPass Checks (Not in Report) ▸
    ├── Select Highway Classification Mismatch
    ├── Select Residential With Multiple Place Nodes
    ├── Select Hamlet/Village Tagging Mismatch
    └── Select Residential Without Highway
```

All items in both submenus run with no time filter, independent of the full QA Check/report.

## Architecture

| File | Purpose |
|---|---|
| `MapathonQAPlugin.java` | Entry point, builds menu (see Menu structure below) |
| `RunFullQAAction.java` | Wizard: HOT TM API → task IDs → JOSM search query |
| `RunQAOnCurrentLayerAction.java` | Runs all 7 checks with progress dialog, generates report |
| `GenerateDemoReportAction.java` | Demo report with realistic simulated issue counts across all 7 checks |
| `SetReportFolderAction.java` | Lets the user override where HTML reports are saved (JOSM preference `mapathonqa.reportDir`) |
| `HistoryLogger.java` | Appends one row per real QA run to a persistent `MapathonQA_history.csv` for tracking quality trends over time — opt-in, off by default |
| `CheckNonYesBuildingTagsAction.java` | Check 1: building ≠ yes (menu: "Select Non-yes Building Tags") |
| `CheckOverlappingBuildingsAction.java` | Check 2: overlapping/contained buildings (matches JOSM's own built-in validator classification — see below), including exact-duplicate ways (`GeometryUtil.isExactDuplicate`) (menu: "Select Overlapping Buildings") |
| `CheckBuildingsOnHighwaysAction.java` | Check 3: buildings crossing roads, excluding `building=roof` (menu: "Select Buildings on Highways") |
| `CheckNonOrthogonalBuildingsAction.java` | Check 4: non-square corners (ported from Mapathoner's `Helper.that_building()`, see Credits) (menu: "Select Non-orthogonal Buildings") |
| `CheckBuildingLayerTagAction.java` | Check 5: buildings with layer=* tag (menu: "Select Buildings with Layer Tag") |
| `CheckBuildingsWithSharedNodesAction.java` | Check 6: shared nodes between buildings and other objects (menu: "Select Buildings with Shared Nodes") |
| `CheckUntaggedWaysAction.java` | Check 7: untagged objects — ways, plus standalone untagged nodes not used as a way vertex (multipolygon members excluded) (menu: "Select Untagged Objects") |
| `GeometryUtil.java` | Ray-casting, segment intersection, exact-duplicate detection, time filter, building-overlap classification via JOSM's own `Geometry.polygonIntersection` |
| `ResidentialArea.java` | Collects landuse=residential areas (closed ways + multipolygon relations, outer/blank-role members stitched into rings; holes ignored) — shared by the residential checks below. `getBBox()` lets checks query `DataSet.searchWays()/searchNodes()` (JOSM's spatial index) scoped to one area instead of scanning the whole layer |
| `SelectResidentialWithoutHighwayAction.java` | Ported from 3rdPassMM: residential areas with no highway way touching/crossing them |
| `SelectResidentialWithMultiplePlaceNodesAction.java` | Ported from 3rdPassMM: residential areas containing more than one `place=*` node |
| `SelectHamletVillageTaggingMismatchAction.java` | Flags a `place=hamlet`/`place=village` node whose enclosing residential area's building count doesn't match: hamlet expects fewer than 15 buildings inside the area, village expects 15 or more |
| `SelectHighwayClassificationMismatchAction.java` | Ported from 3rdPassMM: a highway way whose two endpoints each connect end-to-end to a different, but mutually consistent, `highway=` class (e.g. `path`–`unclassified`–`path`) |
| `QAResults.java` | Data container for all check results |
| `ReportWriter.java` | Generates branded HTML report (MM logo embedded as base64 SVG) |

## HOT TM API

```
GET https://tasking-manager-production-api.hotosm.org/api/v2/projects/{ID}/activities/latest/
```

Returns latest action per task. Plugin filters by `actionDate` within the time window.
All taskStatus values included (MAPPED, VALIDATED, INVALIDATED, BADIMAGERY, READY).