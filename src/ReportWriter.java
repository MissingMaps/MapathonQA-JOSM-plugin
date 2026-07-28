package org.openstreetmap.josm.plugins.mapathonqa;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import org.openstreetmap.josm.spi.preferences.Config;

public class ReportWriter {

    public static final String PREF_REPORT_DIR = "mapathonqa.reportDir";

    /** Brand/structural accent - project link, table header, tip-card border, footer link. */
    private static final String ACCENT = "oklch(0.55 0.13 150)";

    private static final String LOGO_URI = "data:image/svg+xml;base64,PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0idXRmLTgiPz4KPCEtLSBHZW5lcmF0b3I6IEFkb2JlIElsbHVzdHJhdG9yIDI3LjIuMCwgU1ZHIEV4cG9ydCBQbHVnLUluIC4gU1ZHIFZlcnNpb246IDYuMDAgQnVpbGQgMCkgIC0tPgo8c3ZnIHZlcnNpb249IjEuMSIgaWQ9IlZyc3R2YV8xIiB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHhtbG5zOnhsaW5rPSJodHRwOi8vd3d3LnczLm9yZy8xOTk5L3hsaW5rIiB4PSIwcHgiIHk9IjBweCIKCSB2aWV3Qm94PSIwIDAgOTMxIDQ3Ni4yIiBzdHlsZT0iZW5hYmxlLWJhY2tncm91bmQ6bmV3IDAgMCA5MzEgNDc2LjI7IiB4bWw6c3BhY2U9InByZXNlcnZlIj4KPHN0eWxlIHR5cGU9InRleHQvY3NzIj4KCS5zdDB7ZmlsbDpub25lO30KCS5zdDF7ZmlsbDojOUNDNjUzO30KCS5zdDJ7ZmlsbDojRTUyQTI0O30KCS5zdDN7ZmlsbDojRUQ3NzJCO30KCS5zdDR7ZmlsbDojRkVFODE5O30KCS5zdDV7ZmlsbDojNUVDMkVGO30KCS5zdDZ7ZmlsbDojMUQxRDFCO30KPC9zdHlsZT4KPHJlY3QgeT0iMCIgY2xhc3M9InN0MCIgd2lkdGg9IjkzMSIgaGVpZ2h0PSI0NzYuMiIvPgo8cGF0aCBjbGFzcz0ic3QxIiBkPSJNMTM4LjksMTM4LjRoMTgwQzI4OS43LDEwMi45LDI0NS4xLDgwLDE5NS4xLDgwYy0xOS44LDAtMzguNywzLjYtNTYuMSwxMC4xdjQ4LjNIMTM4LjlMMTM4LjksMTM4LjR6Ii8+CjxwYXRoIGNsYXNzPSJzdDIiIGQ9Ik0yOTguNSwyNDMuM2gtMjYzYzAuOSwyOS43LDEwLjMsNTcuMywyNS42LDgwLjdoMjM3LjRWMjQzLjNMMjk4LjUsMjQzLjN6Ii8+CjxwYXRoIGNsYXNzPSJzdDMiIGQ9Ik0zMjcuNCwxNDkuOEgyMzF2ODIuMWgxMjMuNUMzNTMuMiwyMDEuNiwzNDMuNCwxNzMuNCwzMjcuNCwxNDkuOCIvPgo8cGF0aCBjbGFzcz0ic3Q0IiBkPSJNMzEwLjIsMjQzLjN2OTIuMkgxMzlWMzg2YzE3LjQsNi41LDM2LjQsMTAuMSw1Ni4xLDEwLjFjODYuMywwLDE1Ni42LTY3LjksMTU5LjQtMTUyLjdoLTQ0LjNWMjQzLjMKCUwzMTAuMiwyNDMuM3oiLz4KPHBhdGggY2xhc3M9InN0NSIgZD0iTTEyNy4zLDIzMS45Vjk1Qzc0LjgsMTE5LjQsMzgsMTcxLjMsMzUuNywyMzEuOUgxMjcuM3oiLz4KPHBvbHlnb24gY2xhc3M9InN0NiIgcG9pbnRzPSI0OTAuNCwyMDkuNSA0OTAuNCwxNDQuMyA0NjQuOCwxOTIuOSA0NTQuMywxOTIuOSA0MjguNywxNDQuMyA0MjguNywyMDkuNSA0MDkuNCwyMDkuNSA0MDkuNCwxMTEuMSAKCTQzMC4xLDExMS4xIDQ1OS41LDE2Ny4xIDQ4OS4xLDExMS4xIDUwOS43LDExMS4xIDUwOS43LDIwOS41ICIvPgo8cGF0aCBjbGFzcz0ic3Q2IiBkPSJNNTI4LjYsMTA4LjNoMTguOHYxOC40aC0xOC44VjEwOC4zeiBNNTI4LjYsMTM2LjloMTguOHY3Mi42aC0xOC44VjEzNi45TDUyOC42LDEzNi45eiIvPgo8cGF0aCBjbGFzcz0ic3Q2IiBkPSJNNTkyLDIxMC45Yy02LjIsMC0xMi4yLTEtMTgtMi45Yy01LjktMS45LTEwLjktNC43LTE1LjEtOC4zbDctMTEuNmM0LjUsMy4xLDguOCw1LjUsMTMuMSw3LjFzOC41LDIuNCwxMi43LDIuNAoJYzMuNywwLDYuNy0wLjcsOC44LTIuMXMzLjItMy40LDMuMi02cy0xLjMtNC41LTMuOC01LjdzLTYuNi0yLjYtMTIuMy00LjJjLTQuOC0xLjMtOC44LTIuNS0xMi4yLTMuN3MtNi4xLTIuNi04LjEtNC4xCglzLTMuNS0zLjMtNC41LTUuM3MtMS40LTQuNC0xLjQtNy4xYzAtMy43LDAuNy03LDIuMi0xMHMzLjUtNS41LDYuMS03LjZzNS43LTMuNyw5LjItNC44czcuMy0xLjcsMTEuNC0xLjdjNS41LDAsMTAuNywwLjgsMTUuNSwyLjQKCWM0LjgsMS42LDkuMiw0LjEsMTMuMiw3LjZsLTcuNiwxMS4yYy0zLjctMi44LTcuMy00LjgtMTAuOC02LjFzLTctMS45LTEwLjQtMS45Yy0zLjIsMC01LjgsMC42LTgsMS45Yy0yLjIsMS4zLTMuMiwzLjQtMy4yLDYuMgoJYzAsMS4zLDAuMywyLjQsMC44LDMuMnMxLjMsMS42LDIuNSwyLjJzMi42LDEuMyw0LjQsMS45YzEuOCwwLjYsNC4xLDEuMiw2LjgsMS45YzUsMS4zLDkuMywyLjYsMTMsMy45YzMuNiwxLjMsNi41LDIuOCw4LjgsNC40CgljMi4zLDEuNyw0LDMuNiw1LDUuOGMxLjEsMi4yLDEuNiw0LjgsMS42LDcuOGMwLDcuMS0yLjcsMTIuNy04LDE2LjhTNjAxLjEsMjEwLjksNTkyLDIxMC45Ii8+CjxwYXRoIGNsYXNzPSJzdDYiIGQ9Ik02NjEuNSwyMTAuOWMtNi4yLDAtMTIuMi0xLTE4LTIuOXMtMTAuOS00LjctMTUuMS04LjNsNy0xMS42YzQuNSwzLjEsOC44LDUuNSwxMy4xLDcuMXM4LjUsMi40LDEyLjcsMi40CgljMy43LDAsNi43LTAuNyw4LjgtMi4xczMuMi0zLjQsMy4yLTZzLTEuMy00LjUtMy44LTUuN3MtNi42LTIuNi0xMi4zLTQuMmMtNC44LTEuMy04LjgtMi41LTEyLjItMy43cy02LjEtMi42LTguMS00LjEKCXMtMy41LTMuMy00LjUtNS4zcy0xLjQtNC40LTEuNC03LjFjMC0zLjcsMC43LTcsMi4yLTEwczMuNS01LjUsNi4xLTcuNnM1LjctMy43LDkuMi00LjhzNy4zLTEuNywxMS40LTEuN2M1LjUsMCwxMC43LDAuOCwxNS41LDIuNAoJYzQuOCwxLjYsOS4yLDQuMSwxMy4yLDcuNmwtNy42LDExLjJjLTMuNy0yLjgtNy4zLTQuOC0xMC44LTYuMXMtNy0xLjktMTAuNC0xLjljLTMuMiwwLTUuOCwwLjYtOCwxLjljLTIuMiwxLjMtMy4yLDMuNC0zLjIsNi4yCgljMCwxLjMsMC4zLDIuNCwwLjgsMy4yczEuMywxLjYsMi41LDIuMnMyLjYsMS4zLDQuNCwxLjljMS44LDAuNiw0LjEsMS4yLDYuOCwxLjljNSwxLjMsOS4zLDIuNiwxMi45LDMuOWMzLjYsMS4zLDYuNSwyLjgsOC44LDQuNAoJYzIuMywxLjcsNCwzLjYsNSw1LjhjMS4xLDIuMiwxLjYsNC44LDEuNiw3LjhjMCw3LjEtMi43LDEyLjctOCwxNi44UzY3MC43LDIxMC45LDY2MS41LDIxMC45Ii8+CjxwYXRoIGNsYXNzPSJzdDYiIGQ9Ik03MDMuNCwxMDguM2gxOC44djE4LjRoLTE4LjhWMTA4LjN6IE03MDMuNCwxMzYuOWgxOC44djcyLjZoLTE4LjhWMTM2LjlMNzAzLjQsMTM2Ljl6Ii8+CjxwYXRoIGNsYXNzPSJzdDYiIGQ9Ik04MDcuMiwyMDkuNWgtMTguOHYtNDAuN2MwLTUuOC0xLTEwLjEtMy4xLTEyLjhzLTQuOS00LTguNS00Yy0xLjksMC0zLjgsMC40LTUuNywxLjFjLTIsMC43LTMuOCwxLjgtNS41LDMuMQoJcy0zLjMsMi45LTQuNyw0LjhzLTIuNCwzLjktMy4xLDYuMXY0Mi40SDczOXYtNzIuNmgxN3YxMy40YzIuNy00LjYsNi42LTguMiwxMS44LTEwLjhjNS4xLTIuNiwxMC45LTMuOSwxNy4zLTMuOQoJYzQuNiwwLDguMywwLjgsMTEuMiwyLjVzNS4xLDMuOCw2LjcsNi41YzEuNiwyLjcsMi43LDUuNywzLjMsOS4xYzAuNiwzLjQsMC45LDYuOSwwLjksMTAuNFYyMDkuNUw4MDcuMiwyMDkuNXoiLz4KPHBhdGggY2xhc3M9InN0NiIgZD0iTTg1Mi4zLDIwOS45Yy01LDAtOS40LTEtMTMuNC0yLjlzLTcuNS00LjYtMTAuNC04cy01LjItNy4zLTYuOC0xMS43cy0yLjQtOS4xLTIuNC0xNC4xCgljMC01LjMsMC44LTEwLjIsMi41LTE0LjdzNC04LjUsNy0xMS45czYuNi02LjEsMTAuOC04czguOC0yLjksMTMuOC0yLjljNS43LDAsMTAuNywxLjMsMTUsMy44czcuOCw1LjksMTAuNiwxMC4ydi0xMi44aDE2LjRWMjA2CgljMCw1LjQtMSwxMC4yLTMuMSwxNC40cy00LjksNy45LTguNSwxMC44Yy0zLjYsMy03LjksNS4yLTEyLjksNi44cy0xMC40LDIuNC0xNi4zLDIuNGMtOCwwLTE0LjgtMS4zLTIwLjItMy45cy0xMC4xLTYuNC0xNC4xLTExLjIKCWwxMC4yLTkuOGMyLjgsMy40LDYuMyw2LjEsMTAuNiw4YzQuMiwxLjksOC44LDIuOSwxMy41LDIuOWMyLjksMCw1LjctMC40LDguMy0xLjJjMi43LTAuOCw1LTIsNy4xLTMuN2MyLTEuNywzLjctMy44LDQuOC02LjQKCWMxLjItMi42LDEuOC01LjYsMS44LTkuMXYtOS4xYy0yLjQsNC4yLTUuOCw3LjQtMTAuMiw5LjZDODYyLDIwOC43LDg1Ny4zLDIwOS45LDg1Mi4zLDIwOS45IE04NTguNiwxOTVjMiwwLDQtMC4zLDUuOS0xCgljMS45LTAuNiwzLjYtMS41LDUuMi0yLjZjMS42LTEuMSwzLTIuNCw0LjItMy45czIuMS0zLjEsMi44LTQuN3YtMTcuM2MtMS43LTQuMi00LjMtNy43LTcuOS0xMC4yYy0zLjYtMi42LTcuMy0zLjktMTEuMy0zLjkKCWMtMi45LDAtNS41LDAuNi03LjgsMS45cy00LjMsMi45LTYsNXMtMyw0LjUtMy44LDcuMWMtMC45LDIuNy0xLjMsNS40LTEuMyw4LjNzMC41LDUuNywxLjUsOC4zczIuNSw0LjksNC4zLDYuOAoJYzEuOCwxLjksMy45LDMuNSw2LjQsNC42Qzg1MywxOTQuNSw4NTUuNywxOTUuMSw4NTguNiwxOTUiLz4KPHBvbHlnb24gY2xhc3M9InN0NiIgcG9pbnRzPSI0OTAuNCwzMzcuOSA0OTAuNCwyNzIuOCA0NjQuOCwzMjEuMyA0NTQuMywzMjEuMyA0MjguNywyNzIuOCA0MjguNywzMzcuOSA0MDkuNCwzMzcuOSA0MDkuNCwyMzkuNiAKCTQzMC4xLDIzOS42IDQ1OS41LDI5NS41IDQ4OS4xLDIzOS42IDUwOS43LDIzOS42IDUwOS43LDMzNy45ICIvPgo8cGF0aCBjbGFzcz0ic3Q2IiBkPSJNNTQ4LjYsMzM5LjNjLTMuNSwwLTYuOS0wLjYtOS45LTEuN2MtMy4xLTEuMS01LjctMi44LTgtNC45Yy0yLjItMi4xLTQtNC41LTUuMi03LjNzLTEuOS01LjgtMS45LTkuMQoJczAuOC02LjYsMi4zLTkuNXMzLjctNS40LDYuNC03LjRjMi44LTIsNi0zLjYsOS45LTQuOGMzLjgtMS4xLDgtMS43LDEyLjYtMS43YzMuMywwLDYuNSwwLjMsOS42LDAuOHM1LjksMS4zLDguMywyLjR2LTQuMgoJYzAtNC44LTEuNC04LjUtNC4xLTExLjFzLTYuOC0zLjktMTIuMi0zLjljLTMuOSwwLTcuOCwwLjctMTEuNSwyLjFzLTcuNiwzLjQtMTEuNSw2LjFsLTUuNy0xMS44YzkuNC02LjIsMTkuNi05LjMsMzAuNS05LjMKCXMxOC43LDIuNiwyNC42LDcuN3M4LjgsMTIuNSw4LjgsMjIuMnYyMi42YzAsMS45LDAuMywzLjMsMSw0LjJjMC43LDAuOCwxLjgsMS4zLDMuNCwxLjRWMzM4Yy0zLjIsMC42LTUuOSwxLTguMywxCgljLTMuNSwwLTYuMy0wLjgtOC4yLTIuNHMtMy4xLTMuNi0zLjYtNi4ybC0wLjQtNGMtMy4zLDQuMi03LjIsNy41LTExLjksOS43QzU1OC45LDMzOC4yLDU1My45LDMzOS4zLDU0OC42LDMzOS4zIE01NTMuOSwzMjUuOAoJYzMuMiwwLDYuMi0wLjUsOS0xLjdjMi44LTEuMSw1LjEtMi42LDYuNy00LjRjMi0xLjYsMy4xLTMuMywzLjEtNS4zdi04LjNjLTIuMi0wLjgtNC43LTEuNS03LjMtMnMtNS4xLTAuOC03LjYtMC44CgljLTQuOCwwLTguOCwxLjEtMTEuOSwzLjNzLTQuNiw0LjktNC42LDguMmMwLDMuMSwxLjIsNS44LDMuNiw3LjhDNTQ3LjQsMzI0LjcsNTUwLjQsMzI1LjgsNTUzLjksMzI1LjgiLz4KPHBhdGggY2xhc3M9InN0NiIgZD0iTTY1Mi44LDMzOS4zYy01LjgsMC0xMC44LTEuMy0xNS4xLTMuOHMtNy43LTYtMTAuMS0xMC4zdjQyLjNoLTE4LjhWMjY1LjNoMTYuNHYxMi41YzIuNy00LjIsNi4yLTcuNiwxMC41LTEwCgljNC4zLTIuNSw5LjItMy43LDE0LjgtMy43YzQuOSwwLDkuNSwxLDEzLjcsM3M3LjgsNC43LDEwLjgsOGMzLDMuNCw1LjQsNy40LDcuMSwxMS45YzEuNyw0LjYsMi42LDkuNCwyLjYsMTQuNXMtMC44LDEwLjItMi40LDE0LjgKCXMtMy44LDguNi02LjcsMTIuMWMtMi44LDMuNC02LjIsNi4xLTEwLjIsOEM2NjEuOCwzMzguNCw2NTcuNSwzMzkuMyw2NTIuOCwzMzkuMyBNNjQ2LjUsMzIzLjVjMi44LDAsNS40LTAuNiw3LjgtMS44CgljMi40LTEuMiw0LjQtMi44LDYuMS00LjljMS43LTIsMy00LjQsMy45LTcuMXMxLjQtNS41LDEuNC04LjNjMC0zLTAuNS01LjktMS41LTguNXMtMi41LTQuOS00LjMtNi45Yy0xLjgtMS45LTQtMy41LTYuNC00LjYKCWMtMi41LTEuMS01LjItMS43LTgtMS43Yy0xLjgsMC0zLjYsMC4zLTUuNSwwLjlzLTMuNiwxLjUtNS4yLDIuNmMtMS42LDEuMS0zLDIuNC00LjMsMy45cy0yLjIsMy4xLTIuOSw0Ljl2MTcKCWMxLjcsNC4yLDQuMiw3LjYsNy43LDEwLjNDNjM4LjgsMzIyLjIsNjQyLjUsMzIzLjUsNjQ2LjUsMzIzLjUiLz4KPHBhdGggY2xhc3M9InN0NiIgZD0iTTcyNS4zLDMzOS4zYy02LjIsMC0xMi4yLTEtMTgtMi45cy0xMC45LTQuNy0xNS4xLTguM2w3LTExLjZjNC41LDMuMSw4LjgsNS41LDEzLjEsNy4xCgljNC4yLDEuNiw4LjUsMi40LDEyLjcsMi40YzMuNywwLDYuNy0wLjcsOC44LTIuMWMyLjItMS40LDMuMi0zLjQsMy4yLTZzLTEuMy00LjUtMy44LTUuN3MtNi42LTIuNi0xMi4zLTQuMgoJYy00LjgtMS4zLTguOC0yLjUtMTIuMi0zLjdzLTYuMS0yLjYtOC4xLTQuMXMtMy41LTMuMy00LjUtNS4zYy0wLjktMi0xLjQtNC40LTEuNC03LjFjMC0zLjcsMC43LTcsMi4yLTEwczMuNS01LjUsNi4xLTcuNQoJYzIuNi0yLjEsNS43LTMuNyw5LjItNC44czcuMy0xLjcsMTEuNC0xLjdjNS41LDAsMTAuNywwLjgsMTUuNSwyLjRzOS4yLDQuMSwxMy4yLDcuNmwtNy42LDExLjJjLTMuNy0yLjgtNy4zLTQuOC0xMC44LTYuMQoJYy0zLjUtMS4zLTctMS45LTEwLjQtMS45Yy0zLjIsMC01LjgsMC42LTgsMS45cy0zLjIsMy40LTMuMiw2LjJjMCwxLjMsMC4zLDIuNCwwLjgsMy4yYzAuNSwwLjgsMS4zLDEuNiwyLjUsMi4yczIuNiwxLjMsNC40LDEuOQoJYzEuOCwwLjYsNC4xLDEuMiw2LjgsMS45YzUsMS4zLDkuMywyLjYsMTIuOSwzLjlzNi41LDIuOCw4LjgsNC40YzIuMywxLjcsNCwzLjYsNSw1LjhjMS4xLDIuMiwxLjYsNC44LDEuNiw3LjgKCWMwLDcuMS0yLjcsMTIuNy04LDE2LjhTNzM0LjUsMzM5LjMsNzI1LjMsMzM5LjMiLz4KPC9zdmc+Cg==";

    public static File write(QAResults r) throws IOException {
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String projectPart = r.projectId > 0 ? "project" + r.projectId : "standalone";
        String filename = "MapathonQA_" + projectPart + "_" + ts + ".html";
        File outDir = resolveOutputDir();
        File out = new File(outDir, filename);

        int nonYes       = r.nonYesBuildingTags.size();
        int overlap      = r.overlappingBuildings.size();
        int onRoads      = r.buildingsOnHighways.size();
        int nonOrtho     = r.nonOrthogonalBuildings.size();
        int layerTag     = r.buildingsWithLayerTag.size();
        int sharedNodes  = r.buildingsWithSharedNodes.sharedNodeCount;
        int sharedBldgs  = r.buildingsWithSharedNodes.affectedBuildings.size();
        int untagged     = r.untaggedObjects.size();
        int total        = r.totalIssues();

        int mapathonFeatures = r.mapathonFeatures();
        int clean = r.cleanCount();
        int issuesPct = mapathonFeatures > 0 ? Math.round(100f * total / mapathonFeatures) : 0;
        int cleanPct  = mapathonFeatures > 0 ? Math.round(100f * clean / mapathonFeatures) : 100;

        SimpleDateFormat generatedFmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        generatedFmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        String generated = generatedFmt.format(new Date());

        try (BufferedWriter w = new BufferedWriter(new FileWriter(out))) {
            w.write(CSS());

            // ── HEADER ──────────────────────────────────────────────
            w.write("<div class=\'header\'>");
            w.write("<img src=\'" + LOGO_URI + "\' alt=\'Missing Maps\'>");
            w.write("<div class=\'header-divider\'></div>");
            w.write("<div class=\'header-text\'>");
            w.write("<h1>Thank you for organising a mapathon! 🗺️</h1>");
            w.write("<p>Here's some friendly feedback on how it went and what to watch for next time</p>");
            w.write("</div></div>\n");

            w.write("<div class=\'page\'>\n");

            // ── META ────────────────────────────────────────────────
            w.write("<div class=\'meta-card\'>\n");
            if (r.mapathonName != null && !r.mapathonName.trim().isEmpty()) {
                w.write("<div class=\'meta-item\'><div class=\'label\'>Mapathon</div>");
                w.write("<div class=\'value\'>" + esc(r.mapathonName.trim()) + "</div></div>\n");
            }
            if (r.projectId > 0) {
                w.write("<div class=\'meta-item\'><div class=\'label\'>Project</div>");
                w.write("<div class=\'value\'><a href=\'https://tasks.hotosm.org/projects/" + r.projectId + "\' target=\'_blank\'>#" + r.projectId + "</a></div></div>\n");
            }
            if (r.startTime != null && !r.startTime.isEmpty()) {
                w.write("<div class=\'meta-item\'><div class=\'label\'>When</div>");
                w.write("<div class=\'value\'>" + esc(r.startTime) + " &rarr; " + esc(r.endTime) + " (UTC)</div></div>\n");
            }
            w.write("<div class=\'meta-item\'><div class=\'label\'>Mappers</div>");
            w.write("<div class=\'mappers-pill\'>" + r.totalMappers + " contributor" + (r.totalMappers == 1 ? "" : "s") + "</div></div>\n");
            w.write("</div>\n");

            // ── SUMMARY CARDS ────────────────────────────────────────
            w.write("<div class=\'summary-strip\'>\n");
            w.write("<div class=\'summary-card clear\'><div class=\'summary-num\'>" + cleanPct + "%</div>");
            w.write("<div class=\'summary-label\'>" + clean + " object" + (clean == 1 ? "" : "s") + " came out clean &mdash; great mapping!</div></div>\n");
            w.write("<div class=\'summary-card issues\'><div class=\'summary-num\'>" + issuesPct + "%</div><div>");
            w.write("<div class=\'summary-label\'>" + total + " object" + (total == 1 ? "" : "s") + " have a little room to grow</div>");
            if (total > 0) {
                w.write("<div class=\'summary-sub\'>from " + r.issueMappers + " mapper" + (r.issueMappers == 1 ? "" : "s") + "</div>");
            }
            w.write("</div></div>\n");
            w.write("</div>\n");

            // ── ISSUES TABLE ─────────────────────────────────────────
            w.write("<div class=\'card\'>\n<h2>A few things worth a second look</h2>\n");
            w.write("<p class=\'lede\'>Nothing alarming here &mdash; just small tweaks that&#39;ll make the map even better.</p>\n");
            w.write("<div class=\'table-wrap\'>\n<table><thead><tr><th>Check</th><th>Issues</th><th>Notes</th></tr></thead><tbody>\n");
            row(w, "Buildings tagging", nonYes,
                "Buildings tagged differently than building=yes.");
            String overlapNote = "Buildings that geometrically overlap or are contained within another building (each count = one pair).";
            int duplicates = r.overlappingBuildings.duplicateBuildingCount;
            if (duplicates > 0) overlapNote += " " + duplicates + " building(s) were duplicated.";
            row(w, "Overlapping buildings", overlap, overlapNote);
            row(w, "Building outlines that cross a highway", onRoads,
                "Building drawn through an existing highway.");
            row(w, "Non-orthogonal buildings", nonOrtho,
                "Rectangular buildings that most likely should be orthogonal with squared corners.");
            row(w, "Buildings with layer tag", layerTag,
                "Buildings tagged with layer=* created as recommendation from iD editor when two objects are overlapping. The correct solution is for the objects to not overlap.");
            row(w, "Buildings with shared nodes", sharedNodes,
                "Buildings sharing at least one node with another object (each count = one shared node, not a pair; " + sharedBldgs + " building(s) affected).");
            row(w, "Untagged objects", untagged,
                "Nodes and ways with no tags, most likely mappers forgot to add a tag such as building=yes.");
            w.write("</tbody></table>\n</div>\n</div>\n");

            // ── RECOMMENDATIONS ──────────────────────────────────────
            w.write("<div class=\'card\'>\n<h2>Handy tips for your next mapathon</h2>\n");
            w.write("<p class=\'lede\'>Quick reminders to make next time even smoother &mdash; you&#39;ve already got the hang of it!</p>\n");
            w.write("<ul class=\'rec-list\'>\n");
            if (total == 0) {
                w.write("<li class=\'rec-item\'><strong>&#10003; No automated issues detected &mdash; great mapping!</strong></li>\n");
            }
            if (nonYes > 0)      w.write("<li class=\'rec-item\'><strong>Use building=yes for all buildings</strong><p>Unless the project instructions say otherwise or you have local knowledge of the area you are mapping.</p></li>\n");
            if (overlap > 0)     w.write("<li class=\'rec-item\'><strong>Don&#39;t draw a new building overlapping an already existing one</strong><p>Try to draw each building separately. Zoom in and look for outlines already drawn in the area before tracing a new one.</p></li>\n");
            if (onRoads > 0)     w.write("<li class=\'rec-item\'><strong>Do not draw buildings over highways</strong><p>Building outlines should sit beside highways, not on top of them.</p></li>\n");
            if (nonOrtho > 0)    w.write("<li class=\'rec-item\'><strong>Square building corners after drawing</strong><p>Press &ldquo;Q&rdquo; in your mapping editor after drawing a rectangular building outline to straighten the corners. If mapping in JOSM, use the buildings_tools plugin which draws rectangular buildings automatically.</p></li>\n");
            if (layerTag > 0)    w.write("<li class=\'rec-item\'><strong>Avoid using the layer tag on buildings</strong><p>When iD editor warns about overlapping objects it suggests adding layer=*. The correct fix is to move the object instead so it does not overlap, not to add a layer tag.</p></li>\n");
            if (sharedNodes > 0) w.write("<li class=\'rec-item\'><strong>Do not snap buildings to highways or other buildings</strong><p>Each building should have its own independent nodes. In iD editor hold &ldquo;Alt&rdquo; (&ldquo;Ctrl&rdquo; in JOSM) to avoid snapping to existing nodes. If you accidentally connected nodes, use &ldquo;D&rdquo; in iD editor (&ldquo;G&rdquo; in JOSM) to unglue them and then adjust their position.</p></li>\n");
            if (untagged > 0)    w.write("<li class=\'rec-item\'><strong>Always add tags to the nodes and ways you draw</strong><p>A node or way with no tags has no meaning in OpenStreetMap. If you drew a building outline, make sure to add building=yes before saving; if you placed a standalone node, tag it appropriately.</p></li>\n");
            w.write("</ul>\n</div>\n");

            w.write("<div class=\'footer\'>With thanks from <a href=\'https://www.missingmaps.org\'>Missing Maps</a> &mdash; keep mapping!<br>Generated " + generated + " (UTC)</div>\n");
            w.write("</div>\n</body>\n</html>\n");
        }
        return out;
    }

    private static String CSS() {
        return "<!DOCTYPE html>\n<html lang=\'en\'>\n<head>\n"
            + "<meta charset=\'UTF-8\'>\n"
            + "<meta name=\'viewport\' content=\'width=device-width, initial-scale=1\'>\n"
            + "<title>Missing Maps Mapathon Quality Report</title>\n"
            + "<link rel=\'preconnect\' href=\'https://fonts.googleapis.com\'>\n"
            + "<link href=\'https://fonts.googleapis.com/css2?family=Nunito:wght@400;600;700;800&family=Fraunces:opsz,wght@9..144,500;9..144,600&display=swap\' rel=\'stylesheet\'>\n"
            + "<style>\n"
            + "* { box-sizing: border-box; margin: 0; padding: 0; }\n"
            + "body { font-family: \'Nunito\', Arial, sans-serif; background: oklch(0.97 0.015 70); color: oklch(0.28 0.02 60); font-size: 16px; line-height: 1.7; }\n"
            + ".demo-banner { background: " + ACCENT + "; color: #fff; padding: 12px 24px; font-size: 13px; font-weight: 700; text-align: center; letter-spacing: 0.3px; }\n"
            + ".header { background: #fff; padding: 30px 44px; display: flex; align-items: center; gap: 26px; border-bottom: 1px solid oklch(0.93 0.015 70); }\n"
            + ".header img { height: 54px; display: block; }\n"
            + ".header-divider { width: 1px; height: 46px; background: oklch(0.9 0.01 70); }\n"
            + ".header-text h1 { font-family: \'Fraunces\', serif; font-size: 30px; font-weight: 600; color: oklch(0.3 0.03 50); }\n"
            + ".header-text p { font-size: 14px; color: oklch(0.5 0.02 60); margin-top: 4px; }\n"
            + ".page { max-width: 960px; margin: 0 auto; padding: 32px 24px 70px; }\n"
            + ".meta-card { background: #fff; border-radius: 18px; padding: 22px 28px; margin-bottom: 22px; box-shadow: 0 2px 10px oklch(0.3 0.02 60 / 0.06); display: flex; flex-wrap: wrap; gap: 30px; }\n"
            + ".meta-item .label { font-size: 11px; font-weight: 800; color: oklch(0.6 0.02 60); text-transform: uppercase; letter-spacing: 0.6px; }\n"
            + ".meta-item .value { font-size: 15px; color: oklch(0.28 0.02 60); margin-top: 4px; }\n"
            + ".meta-item .value a { color: " + ACCENT + "; text-decoration: none; }\n"
            + ".meta-item .value a:hover { text-decoration: underline; }\n"
            + ".mappers-pill { display: inline-block; margin-top: 3px; padding: 5px 14px; background: oklch(0.94 0.05 220); color: oklch(0.4 0.1 230); font-weight: 700; font-size: 13px; border-radius: 20px; }\n"
            + ".summary-strip { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; margin-bottom: 22px; }\n"
            + ".summary-card { background: #fff; border-radius: 18px; padding: 26px 28px; box-shadow: 0 2px 10px oklch(0.3 0.02 60 / 0.06); border-top: 4px solid #ddd; display: flex; align-items: center; gap: 20px; }\n"
            + ".summary-card.clear { border-top-color: oklch(0.6 0.13 150); }\n"
            + ".summary-card.issues { border-top-color: oklch(0.6 0.13 50); }\n"
            + ".summary-num { font-family: \'Fraunces\', serif; font-size: 44px; font-weight: 500; line-height: 1; flex-shrink: 0; }\n"
            + ".summary-card.clear .summary-num { color: oklch(0.5 0.13 150); }\n"
            + ".summary-card.issues .summary-num { color: oklch(0.55 0.13 50); }\n"
            + ".summary-label { font-size: 14.5px; color: oklch(0.4 0.02 60); }\n"
            + ".summary-sub { display: inline-block; margin-top: 8px; padding: 4px 12px; background: oklch(0.95 0.06 50); color: oklch(0.5 0.13 50); font-weight: 700; font-size: 12px; border-radius: 20px; }\n"
            + ".card { background: #fff; border-radius: 18px; padding: 28px 30px; margin-bottom: 22px; box-shadow: 0 2px 10px oklch(0.3 0.02 60 / 0.06); }\n"
            + ".card h2 { font-family: \'Fraunces\', serif; font-size: 20px; font-weight: 600; color: oklch(0.3 0.03 50); margin: 0 0 6px; }\n"
            + ".card .lede { font-size: 14px; color: oklch(0.5 0.02 60); margin-bottom: 18px; }\n"
            + "table { width: 100%; border-collapse: collapse; }\n"
            + "thead th { background: " + ACCENT + "; color: #fff; padding: 12px 16px; text-align: left; font-size: 13px; font-weight: 700; text-transform: uppercase; letter-spacing: 0.4px; }\n"
            + "thead th:first-child { border-radius: 10px 0 0 10px; }\n"
            + "thead th:last-child { border-radius: 0 10px 10px 0; }\n"
            + "tbody td { padding: 13px 16px; border-bottom: 1px solid oklch(0.95 0.01 70); font-size: 14px; vertical-align: top; }\n"
            + "tbody tr:last-child td { border-bottom: none; }\n"
            + ".count-ok { font-weight: 700; color: oklch(0.5 0.13 150); white-space: nowrap; }\n"
            + ".count-warn { font-weight: 700; color: oklch(0.55 0.13 50); white-space: nowrap; }\n"
            + ".note { color: oklch(0.5 0.02 60); font-size: 13px; }\n"
            + ".rec-list { list-style: none; display: flex; flex-direction: column; gap: 12px; }\n"
            + ".rec-item { padding: 16px 18px; background: oklch(0.97 0.015 70); border-radius: 14px; border-left: 4px solid " + ACCENT + "; }\n"
            + ".rec-item strong { display: block; font-size: 14.5px; color: oklch(0.3 0.03 50); margin-bottom: 3px; }\n"
            + ".rec-item p { font-size: 13.5px; color: oklch(0.48 0.02 60); }\n"
            + ".footer { text-align: center; color: oklch(0.6 0.02 60); font-size: 13px; margin-top: 30px; padding-top: 18px; border-top: 1px solid oklch(0.9 0.01 70); }\n"
            + ".footer a { color: " + ACCENT + "; text-decoration: none; font-weight: 700; }\n"
            + ".table-wrap { overflow-x: auto; }\n"
            + "@media (max-width: 600px) {\n"
            + "  .page { padding: 24px 16px 50px; }\n"
            + "  .header { flex-direction: column; align-items: flex-start; padding: 22px 20px; gap: 12px; }\n"
            + "  .header-divider { display: none; }\n"
            + "  .header-text h1 { font-size: 22px; }\n"
            + "  .header-text p { font-size: 13px; }\n"
            + "  .meta-card, .card { padding: 20px 18px; }\n"
            + "  .summary-strip { grid-template-columns: 1fr; }\n"
            + "  .summary-card { padding: 20px 20px; gap: 16px; }\n"
            + "  .summary-num { font-size: 36px; }\n"
            + "  thead th, tbody td { padding: 10px 12px; font-size: 13px; }\n"
            + "}\n"
            + "</style>\n</head>\n<body>\n";
    }

    public static File writeDemoReport(QAResults r) throws IOException {
        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String filename = "MapathonQA_DEMO_" + ts + ".html";
        File outDir = resolveOutputDir();
        File out = new File(outDir, filename);

        File tmp = write(r);
        String html = new String(java.nio.file.Files.readAllBytes(tmp.toPath()), "UTF-8");
        String banner = "<div class=\'demo-banner\'>Demonstration report &mdash; all figures are simulated, not real OSM data</div>\n";
        html = html.replaceFirst("<div class=\'header\'>", banner + "<div class=\'header\'>");
        try (java.io.FileWriter fw = new java.io.FileWriter(out)) { fw.write(html); }
        tmp.delete();
        return out;
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

    private static void row(BufferedWriter w, String check, int count, String notes) throws IOException {
        String cls = count == 0 ? "count-ok" : "count-warn";
        String countStr = count == 0 ? "&#10003; None" : String.valueOf(count);
        w.write("<tr><td>" + esc(check) + "</td><td class=\'" + cls + "\'>" + countStr + "</td><td class=\'note\'>" + esc(notes) + "</td></tr>\n");
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
