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

    public MapathonQAPlugin(PluginInformation info) {
        super(info);

        MainMenu menu = MainApplication.getMenu();
        JMenu menuRoot = menu.addMenu(
            I18n.tr("MapathonQA"), I18n.tr("MapathonQA"), 0,
            menu.getDefaultMenuPos(), HelpUtil.ht("Plugin/MapathonQA"));

        menuRoot.add(new JMenuItem(new RunFullQAAction()));
        menuRoot.add(new JMenuItem(new RunQAOnCurrentLayerAction()));
        menuRoot.addSeparator();
        menuRoot.add(new JMenuItem(new GenerateDemoReportAction()));
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

        // Ported from 3rdPassMM - kept in their own submenu (not mixed with the checks above)
        // since these do NOT contribute to the QA report/RunQAOnCurrentLayerAction pipeline.
        JMenu thirdPassChecks = new JMenu(I18n.tr("3rdPass Checks (Not in Report)"));
        thirdPassChecks.add(new JMenuItem(new SelectHighwayClassificationMismatchAction()));
        thirdPassChecks.add(new JMenuItem(new SelectResidentialWithMultiplePlaceNodesAction()));
        thirdPassChecks.add(new JMenuItem(new SelectResidentialWithoutHighwayAction()));
        menuRoot.add(thirdPassChecks);
    }
}
