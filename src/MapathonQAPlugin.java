package org.openstreetmap.josm.plugins.mapathonqa;

import javax.swing.JMenu;
import javax.swing.JMenuItem;

import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MainMenu;
import org.openstreetmap.josm.gui.help.HelpUtil;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.tools.I18n;

public class MapathonQAPlugin extends Plugin {

    public static int lastProjectId = 0;
    public static String lastStart  = "";
    public static String lastEnd    = "";
    public static String lastMapathonName = "";

    /** Shared body style for HTML-formatted JLabels, so all plugin dialogs use the same font/size. */
    static final String LABEL_STYLE = "font-family:sans-serif; font-size:12px;";

    /** Wraps content in an {@code <html><body>} tag using the plugin's shared dialog font style. */
    static String html(String bodyContent) {
        return "<html><body style='" + LABEL_STYLE + "'>" + bodyContent + "</body></html>";
    }

    public MapathonQAPlugin(PluginInformation info) {
        super(info);

        MainMenu menu = MainApplication.getMenu();
        JMenu menuRoot = menu.addMenu(
            I18n.tr("MapathonQA"), I18n.tr("MapathonQA"), 0,
            menu.getDefaultMenuPos(), HelpUtil.ht("Plugin/MapathonQA"));

        menuRoot.add(new JMenuItem(new GenerateMapathonReportAction()));
        menuRoot.add(new JMenuItem(new RunQAOnCurrentLayerAction()));
        menuRoot.addSeparator();
        menuRoot.add(new JMenuItem(new SetReportFolderAction()));
        menuRoot.addSeparator();

        // Standalone "select matching objects" actions - no time filter, run on demand.
        JMenu individualChecks = new JMenu(I18n.tr("Individual Checks"));
        individualChecks.add(new JMenuItem(new CheckNonYesBuildingTagsAction()));
        individualChecks.add(new JMenuItem(new CheckOverlappingBuildingsAction()));
        individualChecks.add(new JMenuItem(new CheckBuildingsOnHighwaysAction()));
        individualChecks.add(new JMenuItem(new CheckNonOrthogonalBuildingsAction()));
        individualChecks.add(new JMenuItem(new CheckBuildingLayerTagAction()));
        individualChecks.add(new JMenuItem(new CheckBuildingsWithSharedNodesAction()));
        individualChecks.add(new JMenuItem(new CheckUntaggedWaysAction()));
        menuRoot.add(individualChecks);
    }
}
