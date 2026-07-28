package org.openstreetmap.josm.plugins.mapathonqa;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.io.File;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.I18n;

public class SetReportFolderAction extends AbstractAction {

    public SetReportFolderAction() {
        super(I18n.tr("Set Report Save Folder..."));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        JDialog dlg = new JDialog((java.awt.Frame) null, "MapathonQA – Report Save Folder", true);
        dlg.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        dlg.setSize(520, 210);
        dlg.setLocationRelativeTo(null);
        RunFullQAAction.bindEscapeToClose(dlg);

        JPanel main = new JPanel(new GridBagLayout());
        main.setBorder(BorderFactory.createEmptyBorder(16, 20, 8, 20));
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 4, 6, 4); gc.anchor = GridBagConstraints.WEST;
        gc.fill = GridBagConstraints.HORIZONTAL; gc.weightx = 1.0;

        gc.gridx=0; gc.gridy=0; gc.gridwidth=2;
        main.add(new JLabel("<html><b>Where should MapathonQA save HTML reports?</b><br>"
            + "<span style='color:#555555'>Leave blank to use the default (Downloads folder, falling back to Desktop, then home).</span></html>"), gc);

        String current = Config.getPref().get(ReportWriter.PREF_REPORT_DIR, "");
        gc.gridy=1; gc.gridwidth=1;
        JTextField pathField = new JTextField(current, 30);
        gc.gridx=0; gc.weightx=1.0; main.add(pathField, gc);
        JButton btnBrowse = new JButton("Browse…");
        gc.gridx=1; gc.weightx=0; main.add(btnBrowse, gc);

        btnBrowse.addActionListener(ev -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle("Select Report Folder");
            chooser.setApproveButtonText("Select Folder");
            chooser.setApproveButtonToolTipText("Use this folder for saved reports");
            String typed = pathField.getText().trim();
            if (!typed.isEmpty()) {
                File f = new File(typed);
                if (f.isDirectory()) chooser.setCurrentDirectory(f);
            }
            if (chooser.showOpenDialog(dlg) == JFileChooser.APPROVE_OPTION) {
                pathField.setText(chooser.getSelectedFile().getAbsolutePath());
            }
        });

        JPanel btns = new JPanel();
        JButton btnReset  = new JButton("Reset to Default");
        JButton btnSave   = new JButton("Save");
        JButton btnCancel = new JButton("Cancel");
        btns.add(btnReset); btns.add(btnSave); btns.add(btnCancel);

        btnReset.addActionListener(ev -> pathField.setText(""));
        btnCancel.addActionListener(ev -> dlg.dispose());
        btnSave.addActionListener(ev -> {
            String path = pathField.getText().trim();
            if (!path.isEmpty() && !new File(path).isDirectory()) {
                JOptionPane.showMessageDialog(dlg,
                    "That folder doesn't exist. Please choose a valid folder, or leave it blank to use the default.",
                    "MapathonQA", JOptionPane.ERROR_MESSAGE);
                return;
            }
            Config.getPref().put(ReportWriter.PREF_REPORT_DIR, path);
            dlg.dispose();
            JOptionPane.showMessageDialog(null,
                path.isEmpty()
                    ? "Reports will be saved to the default location (Downloads folder)."
                    : "Reports will be saved to:\n" + path,
                "MapathonQA", JOptionPane.INFORMATION_MESSAGE);
        });

        dlg.setLayout(new BorderLayout());
        dlg.add(main, BorderLayout.CENTER);
        dlg.add(btns, BorderLayout.SOUTH);
        dlg.setVisible(true);
    }
}
