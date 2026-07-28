# MapathonQA – JOSM Plugin

Post-mapathon data quality checker for Missing Maps.

## License

GPLv3 — see [`LICENSE`](LICENSE). JOSM core is "GPLv2 or later," which makes GPLv3 a compatible
choice for plugins built against it.

## Credits

The non-orthogonal building check (`CheckNonOrthogonalBuildingsAction.java`) ports the
classification logic from **Mapathoner**'s `Helper.that_building()` — same thresholds, same
branch structure — reimplemented here using JOSM's own `Way.getAngles()`. Mapathoner is by
qeef: https://mapathoner.mapathon.cz/

This plugin was built with the help of Claude, Anthropic's AI chatbot, used throughout for design,
implementation, and debugging.

## Build

From this repo's root folder (needs JDK 17+):

1. Download `josm-tested.jar` from https://josm.openstreetmap.de/josm-tested.jar into `lib/` (gitignored, not committed).
2. **Windows:** `build.bat`
   **Linux/Mac:** `./build.sh`

This produces `MapathonQA.jar` in this folder — copy to JOSM's plugins folder and restart JOSM.

## Critical JOSM API notes (learned from bytecode inspection)

- Use `getRawTimestamp()` (returns int, Unix seconds) — NOT `getTimestamp()` (wrong return type in JOSM 19555)
- Use `MainApplication.getLayerManager().getEditDataSet()` — NOT `getActiveDataLayer()`
- Add menu items with `menuRoot.add(new JMenuItem(action))` — NOT `MainMenu.add()`
- `addMenu()` signature: `(name, tooltip, mnemonic, position, helpId)`
- `SimpleDateFormat` MUST use `sdf.setTimeZone(TimeZone.getTimeZone("UTC"))` — all times are UTC
- `Way.getAngles()` (used by the non-orthogonal check) returns corner angles computed from
  projected `EastNorth` coordinates — no manual lat/lon correction needed
- `GeometryUtil`'s ray-casting/segment-intersection checks use raw lat/lon directly (no
  projection correction — fine at the scale of a single building/task)
- Building-vs-building overlap uses `Geometry.getAreaEastNorth()` + `Geometry.polygonIntersection(Area,
  Area, 1.0E-4)` — the same public JOSM API and epsilon backing the built-in validator's own
  "Overlapping buildings"/"Building inside building" MapCSS rules (`data/validator/geometry.mapcss`
  in josm-tested.jar), so counts agree with JOSM's Validation Results panel instead of drifting from
  a hand-rolled heuristic

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
| `ResidentialArea.java` | Collects landuse=residential areas (closed ways + multipolygon relations, outer/blank-role members stitched into rings; holes ignored) — shared by the two residential checks below |
| `SelectResidentialWithoutHighwayAction.java` | Ported from 3rdPassMM: residential areas with no highway way touching/crossing them |
| `SelectResidentialWithMultiplePlaceNodesAction.java` | Ported from 3rdPassMM: residential areas containing more than one `place=*` node |
| `SelectHighwayClassificationMismatchAction.java` | Ported from 3rdPassMM: a highway way whose two endpoints each connect end-to-end to a different, but mutually consistent, `highway=` class (e.g. `path`–`unclassified`–`path`) |
| `QAResults.java` | Data container for all check results |
| `ReportWriter.java` | Generates branded HTML report (MM logo embedded as base64 SVG) |

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
    └── Select Residential Without Highway
```

All items in both submenus run with no time filter (see Time filtering below) — they select
matching objects in the whole current layer on demand, independent of the full QA pipeline/report.
"Individual Checks" mirrors the 7 checks in the full report exactly (same logic, same thresholds),
so running one standalone is a good way to spot-check or build trust in a specific check's result.
"3rdPass Checks (Not in Report)" holds the three checks ported from
[3rdPassJOSMPlugin](https://github.com/MissingMaps/3rdPassJOSMPlugin) — kept in their own submenu,
named explicitly, so it's clear at a glance that these don't feed into the QA report/history CSV.

## Time filtering

All checks accept `(DataSet ds, Date since, Date until)`. Objects are only flagged if
`getRawTimestamp() >= since && getRawTimestamp() <= until`. Objects with timestamp=0
(unknown) are always included. The since/until dates are stored in `MapathonQAPlugin.lastStart`
/ `lastEnd` (strings) and parsed as UTC in `RunQAOnCurrentLayerAction.parseStartTime()`.

## HOT TM API

```
GET https://tasking-manager-production-api.hotosm.org/api/v2/projects/{ID}/activities/latest/
```

Returns latest action per task. Plugin filters by `actionDate` within the time window.
All taskStatus values included (MAPPED, VALIDATED, INVALIDATED, BADIMAGERY, READY).
Task grid loaded via OpenLocationAction reflection (tries 3 method signatures for compat).

## Report

`ReportWriter.write(QAResults)` generates a self-contained HTML file with:
- Missing Maps logo embedded as base64 SVG (passed as constant string `LOGO_URI`)
- Warm, mobile-responsive design (Nunito/Fraunces via Google Fonts, OKLCH accent color) with a
  friendly "thank you for organising a mapathon" tone rather than a clinical report feel
- Meta strip, two summary cards, issues table, recommendations
- Saved to the folder configured via **MapathonQA → Set Report Save Folder...** (JOSM preference
  `mapathonqa.reportDir`); if unset or invalid, falls back to ~/Downloads/, then Desktop, then home
- Overlapping-buildings row note gets a trailing "N building(s) were duplicated." clause appended
  only when `QAResults.overlappingBuildings.duplicateBuildingCount > 0` — no separate report row
  or column for duplicates, they're folded into the existing overlap count/selection

`ReportWriter.writeDemoReport(QAResults)` wraps `write()` and injects a blue demo banner.

## History log

Opt-in via a checkbox on the Step 1 (Project & Time Window) dialog of **Run Full QA Check...**
— off by default, remembered as a JOSM preference (`mapathonqa.includeInHistory`) once set, so it
applies to subsequent **Run QA on Current Layer** runs too. When enabled, `HistoryLogger.appendRow(QAResults)`
appends one CSV row per real `Run QA on Current Layer`
execution to `MapathonQA_history.csv`, in the same folder as HTML reports. The file is created
with a header + UTF-8 BOM (so Excel renders it correctly) on first use, then only ever appended
to — never overwritten — so a series of mapathons accumulates in one file that can be opened
in Excel/Sheets to chart quality score, issue counts, etc. over time. Only wired into
`RunQAOnCurrentLayerAction`, deliberately **not** called from `GenerateDemoReportAction` — demo
runs use fake data and must not pollute the real history.
